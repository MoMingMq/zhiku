package org.zhiku.ai.tool.execute;

import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.json.utils.JsonUtils;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工具模板渲染器：{{参数}} 单次替换 + 按位置编码
 * <p>防注入铁律：替换结果不再参与模板解析；URL 的 scheme+host 由保存校验保证无占位。
 *
 * @author zhiku
 */
@Component
public class ToolTemplateRenderer {

    /**
     * 占位符语法 {{name}}（name 限字母数字下划线）
     */
    static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z0-9_]+)\\}\\}");

    /**
     * 渲染结果：url=path 已渲染的 URL；urlWithQuery=追加 query 串；headers=键值对（值已渲染未编码）；body=按 content-type 转义后
     */
    public record RenderedRequest(String url, String urlWithQuery, Map<String, String> headers, String body) {
    }

    /**
     * 渲染模板
     *
     * @param urlTemplate  URL 模板（占位仅允许出现在 path）
     * @param headersJson  请求头 JSON 数组 [{key,value}]
     * @param queryJson    查询参数 JSON 数组 [{key,value}]
     * @param bodyTemplate 请求体模板（可空）
     * @param contentType  请求体类型（可空，决定 body 转义策略）
     * @param params       参数值
     */
    public RenderedRequest render(String urlTemplate, String headersJson, String queryJson,
                                  String bodyTemplate, String contentType, Map<String, Object> params) {
        String url = renderUrlPath(urlTemplate, params);
        Map<String, String> headers = renderKeyValue(headersJson, params);
        Map<String, String> queryPairs = renderKeyValue(queryJson, params);
        String urlWithQuery = appendQuery(url, queryPairs);
        String body = renderBody(bodyTemplate, contentType, params);
        return new RenderedRequest(url, urlWithQuery, headers, body);
    }

    private String renderUrlPath(String template, Map<String, Object> params) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(encPath(requireParam(matcher.group(1), params))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Map<String, String> renderKeyValue(String json, Map<String, Object> params) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return map;
        }
        List<KeyValue> pairs = JsonUtils.parseArray(json, KeyValue.class);
        for (KeyValue kv : pairs) {
            if (kv.key() == null || kv.key().isBlank()) {
                continue;
            }
            map.put(kv.key(), renderPlain(kv.value(), params));
        }
        return map;
    }

    private String appendQuery(String url, Map<String, String> queryPairs) {
        if (queryPairs.isEmpty()) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url);
        sb.append(url.contains("?") ? '&' : '?');
        for (Map.Entry<String, String> entry : queryPairs.entrySet()) {
            sb.append(enc(entry.getKey())).append('=').append(enc(entry.getValue())).append('&');
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private String renderBody(String template, String contentType, Map<String, Object> params) {
        if (template == null || template.isBlank()) {
            return null;
        }
        boolean json = contentType != null && contentType.toLowerCase().contains("json");
        boolean form = contentType != null && contentType.toLowerCase().contains("x-www-form-urlencoded");
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = requireParam(matcher.group(1), params);
            String replacement = json ? jsonEscape(value) : (form ? enc(value) : value);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 头/query 值渲染：原样（query 在拼接时统一编码）
     */
    private String renderPlain(String template, Map<String, Object> params) {
        if (template == null) {
            return "";
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(requireParam(matcher.group(1), params)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String requireParam(String name, Map<String, Object> params) {
        Object value = params == null ? null : params.get(name);
        if (value == null) {
            throw new ServiceException("缺少必填参数: " + name);
        }
        return String.valueOf(value);
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encPath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * JSON 字符串字面量转义（置于 "..." 之间的安全形式）
     */
    private String jsonEscape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * 头/query JSON 数组条目
     */
    public record KeyValue(String key, String value) {
    }
}
