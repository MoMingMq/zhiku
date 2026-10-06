package org.zhiku.ai.tool.execute;

import cn.hutool.extra.spring.SpringUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zhiku.ai.tool.domain.AiTool;
import org.zhiku.ai.tool.service.IAiToolCallLogService;
import org.zhiku.common.core.exception.ServiceException;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 工具执行器单测（MockRestServiceServer 绑定 RestClient；SsrfGuard 真实执行，工具开内网白名单放行 localhost）
 * <p>JsonUtils 静态初始化依赖 Spring 容器：@BeforeAll 注册最小 StaticApplicationContext（对齐 AiModelServiceTest 先例）。
 *
 * @author zhiku
 */
class ToolExecutorTest {

    private static final String JSON = "{\"code\":0}";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private IAiToolCallLogService logService;
    private ToolExecutor executor;

    @BeforeAll
    static void initSpringUtil() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("jsonMapper", tools.jackson.databind.json.JsonMapper.builder().build());
        context.refresh();
        new SpringUtil().setApplicationContext(context);
    }

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        logService = mock(IAiToolCallLogService.class);
        executor = new ToolExecutor(new ToolTemplateRenderer(), new SsrfGuard(), logService) {
            @Override
            protected RestClient createRestClient(int timeoutSeconds) {
                return builder.build();
            }
        };
    }

    private AiTool tool(String method, String url, String query, String body) {
        AiTool tool = new AiTool();
        tool.setToolId(1L);
        tool.setToolName("weather_query");
        tool.setDescription("查天气");
        tool.setMethod(method);
        tool.setUrlTemplate(url);
        tool.setQueryParams(query);
        tool.setBodyTemplate(body);
        tool.setParamSchema("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}},\"required\":[\"city\"]}");
        tool.setAuthType("0");
        tool.setAllowIntranet("1");
        tool.setTimeoutSeconds(5);
        tool.setStatus("0");
        return tool;
    }

    @Test
    void successCallReturnsBodyAndLogs() {
        server.expect(requestTo("http://127.0.0.1:9999/w?city=%E5%8C%97%E4%BA%AC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));
        ToolExecResult result = executor.execute(tool("GET", "http://127.0.0.1:9999/w",
            "[{\"key\":\"city\",\"value\":\"{{city}}\"}]", null), Map.of("city", "北京"), ToolCallContext.testRun(1L));
        assertTrue(result.success());
        assertEquals(JSON, result.body());
        verify(logService).insertLog(any());
    }

    @Test
    void missingRequiredParamFailsBeforeHttp() {
        // 执行器对预检失败（缺参/SSRF）不抛异常，返回可读错误结果（对话链路不打断）
        ToolExecResult result = executor.execute(tool("GET", "http://127.0.0.1:9999/w",
            "[{\"key\":\"city\",\"value\":\"{{city}}\"}]", null), Map.of(), ToolCallContext.testRun(1L));
        assertFalse(result.success());
        assertTrue(result.errorMsg().contains("缺少必填参数"));
        verify(logService).insertLog(any());
    }

    @Test
    void serverErrorProducesReadableMessage() {
        server.expect(requestTo("http://127.0.0.1:9999/w?city=x"))
            .andRespond(withServerError().body("boom"));
        ToolExecResult result = executor.execute(tool("GET", "http://127.0.0.1:9999/w",
            "[{\"key\":\"city\",\"value\":\"{{city}}\"}]", null), Map.of("city", "x"), ToolCallContext.testRun(1L));
        assertFalse(result.success());
        assertTrue(result.errorMsg().contains("HTTP 500"));
        verify(logService).insertLog(any());
    }

    @Test
    void oversizedBodyIsTruncated() {
        String big = "x".repeat(9 * 1024);
        server.expect(requestTo("http://127.0.0.1:9999/w?city=x"))
            .andRespond(withSuccess(big, MediaType.TEXT_PLAIN));
        ToolExecResult result = executor.execute(tool("GET", "http://127.0.0.1:9999/w",
            "[{\"key\":\"city\",\"value\":\"{{city}}\"}]", null), Map.of("city", "x"), ToolCallContext.testRun(1L));
        assertTrue(result.body().length() < 9 * 1024);
        assertTrue(result.body().contains("已截断"));
    }

    @Test
    void binaryContentReturnsPlaceholder() {
        server.expect(requestTo("http://127.0.0.1:9999/w?city=x"))
            .andRespond(withSuccess("fake-bytes".getBytes(), MediaType.IMAGE_PNG));
        ToolExecResult result = executor.execute(tool("GET", "http://127.0.0.1:9999/w",
            "[{\"key\":\"city\",\"value\":\"{{city}}\"}]", null), Map.of("city", "x"), ToolCallContext.testRun(1L));
        assertTrue(result.body().contains("二进制"));
    }

    @Test
    void intranetBlockedWithoutFlag() {
        AiTool t = tool("GET", "http://127.0.0.1:9999/w", "[{\"key\":\"city\",\"value\":\"{{city}}\"}]", null);
        t.setAllowIntranet("0");
        ToolExecResult result = executor.execute(t, Map.of("city", "x"), ToolCallContext.testRun(1L));
        assertFalse(result.success());
        assertTrue(result.errorMsg().contains("不允许访问"));
        verify(logService).insertLog(any());
    }

    @Test
    void bearerAuthHeaderInjected() {
        server.expect(requestTo("http://127.0.0.1:9999/w?city=x"))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("Authorization", "Bearer sk-test"))
            .andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));
        AiTool t = tool("GET", "http://127.0.0.1:9999/w", "[{\"key\":\"city\",\"value\":\"{{city}}\"}]", null);
        t.setAuthType("1");
        t.setAuthValue("sk-test");
        ToolExecResult result = executor.execute(t, Map.of("city", "x"), ToolCallContext.testRun(1L));
        assertTrue(result.success());
    }
}
