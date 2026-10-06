package org.zhiku.ai.tool.execute;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zhiku.ai.tool.domain.AiTool;
import org.zhiku.ai.tool.domain.AiToolCallLog;
import org.zhiku.ai.tool.service.IAiToolCallLogService;
import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.json.utils.JsonUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 工具执行器：参数校验→模板渲染→SSRF 校验→鉴权注入→HTTP 调用→响应处理→审计
 * <p>试运行与对话调用共用本实现（防"测试通过线上不行"）；失败不抛异常，返回可读错误供模型/前端消费。
 * <p>createRestClient 为 protected，单测覆写注入 mock。
 *
 * @author zhiku
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutor {

    /**
     * 响应体截断上限（8KB）
     */
    static final int MAX_RESPONSE_CHARS = 8 * 1024;

    /**
     * 截断标注
     */
    static final String TRUNCATED_MARK = "\n[内容过长已截断]";

    /**
     * 视为二进制的 content-type 精确值与前缀
     */
    private static final Set<String> BINARY_TYPES = Set.of("application/octet-stream", "application/pdf", "application/zip");
    private static final Set<String> BINARY_PREFIXES = Set.of("image/", "audio/", "video/");

    private final ToolTemplateRenderer renderer;
    private final SsrfGuard ssrfGuard;
    private final IAiToolCallLogService logService;

    /**
     * 执行工具（对外唯一入口）
     */
    public ToolExecResult execute(AiTool tool, Map<String, Object> params, ToolCallContext context) {
        long start = System.currentTimeMillis();
        ToolTemplateRenderer.RenderedRequest rendered = null;
        Integer httpStatus = null;
        String responseSnapshot = null;
        String errorMsg = null;
        try {
            rendered = renderer.render(tool.getUrlTemplate(), tool.getHeaders(), tool.getQueryParams(),
                tool.getBodyTemplate(), findContentType(tool.getHeaders()), params);
            ssrfGuard.check(URI.create(rendered.url()), "1".equals(tool.getAllowIntranet()));
            RestClient.RequestBodySpec spec = createRestClient(boundedTimeout(tool))
                .method(HttpMethod.valueOf(tool.getMethod()))
                .uri(URI.create(rendered.urlWithQuery()));
            rendered.headers().forEach(spec::header);
            injectAuth(tool, spec);
            if (rendered.body() != null) {
                spec.body(rendered.body());
            }
            ToolExecResult result = spec.exchange((request, response) -> {
                int code = response.getStatusCode().value();
                String bodyText = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
                MediaType contentType = response.getHeaders().getContentType();
                if (code >= 200 && code < 300) {
                    return new ToolExecResult(true, code, processBody(bodyText, contentType), null,
                        System.currentTimeMillis() - start);
                }
                return new ToolExecResult(false, code, processBody(bodyText, contentType),
                    "HTTP " + code + ": " + truncate(bodyText, 500), System.currentTimeMillis() - start);
            });
            httpStatus = result.httpStatus();
            responseSnapshot = result.body();
            errorMsg = result.errorMsg();
            return result;
        } catch (ServiceException e) {
            errorMsg = e.getMessage();
            return new ToolExecResult(false, null, errorMsg, errorMsg, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("工具 {} 调用异常", tool.getToolName(), e);
            errorMsg = "工具调用异常: " + e.getMessage();
            return new ToolExecResult(false, null, errorMsg, errorMsg, System.currentTimeMillis() - start);
        } finally {
            logService.insertLog(buildLog(tool, context, rendered, responseSnapshot, httpStatus, errorMsg,
                System.currentTimeMillis() - start));
        }
    }

    /**
     * 响应体处理：二进制说明 + 8KB 截断
     */
    private String processBody(String body, MediaType contentType) {
        if (isBinary(contentType)) {
            return "[工具返回二进制内容，不支持展示/回填模型]";
        }
        return body != null && body.length() > MAX_RESPONSE_CHARS
            ? body.substring(0, MAX_RESPONSE_CHARS) + TRUNCATED_MARK : body;
    }

    private boolean isBinary(MediaType contentType) {
        if (contentType == null) {
            return false;
        }
        String raw = contentType.toString().toLowerCase(Locale.ROOT);
        return BINARY_TYPES.contains(raw) || BINARY_PREFIXES.stream().anyMatch(raw::startsWith);
    }

    /**
     * 从 headers JSON 中取 Content-Type（键大小写不敏感；无则 null）
     */
    static String findContentType(String headersJson) {
        if (headersJson == null || headersJson.isBlank()) {
            return null;
        }
        for (ToolTemplateRenderer.KeyValue kv : JsonUtils.parseArray(headersJson, ToolTemplateRenderer.KeyValue.class)) {
            if ("content-type".equalsIgnoreCase(kv.key())) {
                return kv.value();
            }
        }
        return null;
    }

    private void injectAuth(AiTool tool, RestClient.RequestBodySpec spec) {
        String type = tool.getAuthType() == null ? "0" : tool.getAuthType();
        if ("1".equals(type)) {
            spec.header("Authorization", "Bearer " + tool.getAuthValue());
        } else if ("2".equals(type) && tool.getAuthKey() != null) {
            spec.header(tool.getAuthKey(), tool.getAuthValue());
        }
    }

    private int boundedTimeout(AiTool tool) {
        int t = tool.getTimeoutSeconds() == null ? 10 : tool.getTimeoutSeconds();
        return Math.min(Math.max(t, 1), 60);
    }

    private AiToolCallLog buildLog(AiTool tool, ToolCallContext context,
                                   ToolTemplateRenderer.RenderedRequest rendered,
                                   String responseSnapshot, Integer httpStatus, String errorMsg, long durationMs) {
        AiToolCallLog logRow = new AiToolCallLog();
        logRow.setToolId(tool.getToolId());
        logRow.setToolName(tool.getToolName());
        logRow.setAppId(context.appId());
        logRow.setMessageId(context.messageId());
        logRow.setScene(context.scene());
        logRow.setRequestSnapshot(snapshotRequest(tool, rendered));
        logRow.setResponseSnapshot(truncate(responseSnapshot, MAX_RESPONSE_CHARS));
        logRow.setHttpStatus(httpStatus);
        logRow.setSuccess(errorMsg == null ? "1" : "0");
        logRow.setErrorMsg(truncate(errorMsg, 1000));
        logRow.setDurationMs((int) durationMs);
        logRow.setCreateBy(context.operatorId());
        logRow.setCreateTime(new java.util.Date());
        return logRow;
    }

    private String snapshotRequest(AiTool tool, ToolTemplateRenderer.RenderedRequest rendered) {
        if (rendered == null) {
            return "（渲染前失败，无请求快照）";
        }
        Map<String, String> safeHeaders = new LinkedHashMap<>(rendered.headers());
        safeHeaders.put("Authorization", "***");
        if (tool.getAuthKey() != null) {
            safeHeaders.put(tool.getAuthKey(), "***");
        }
        return JsonUtils.toJsonString(Map.of(
            "method", tool.getMethod(), "url", rendered.urlWithQuery(),
            "headers", safeHeaders, "body", rendered.body() == null ? "" : rendered.body()));
    }

    private String truncate(String text, int max) {
        return text == null ? null : (text.length() > max ? text.substring(0, max) : text);
    }

    /**
     * 构造带超时的 RestClient（protected：单测覆写注入 mock）
     */
    protected RestClient createRestClient(int timeoutSeconds) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(timeoutSeconds))
            .build();
        org.springframework.http.client.JdkClientHttpRequestFactory factory =
            new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return RestClient.builder().requestFactory(factory).build();
    }
}
