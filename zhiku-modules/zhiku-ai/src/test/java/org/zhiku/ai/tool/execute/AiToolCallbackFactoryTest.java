package org.zhiku.ai.tool.execute;

import org.junit.jupiter.api.Test;
import org.zhiku.ai.tool.domain.AiTool;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Spring AI 工具回调工厂单测
 *
 * @author zhiku
 */
class AiToolCallbackFactoryTest {

    private AiTool tool() {
        AiTool tool = new AiTool();
        tool.setToolId(9L);
        tool.setToolName("weather_query");
        tool.setDescription("查询城市天气");
        tool.setParamSchema("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}");
        return tool;
    }

    @Test
    void buildsCallbackWithNameDescriptionSchema() {
        ToolExecutor executor = mock(ToolExecutor.class);
        org.springframework.ai.tool.ToolCallback callback = new AiToolCallbackFactory(executor).build(tool());
        assertEquals("weather_query", callback.getToolDefinition().name());
        assertEquals("查询城市天气", callback.getToolDefinition().description());
        assertEquals("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}",
            callback.getToolDefinition().inputSchema());
    }

    @Test
    void callDelegatesToExecutorWithToolContext() {
        ToolExecutor executor = mock(ToolExecutor.class);
        AiTool tool = tool();
        when(executor.execute(eq(tool), any(), any())).thenReturn(
            new ToolExecResult(true, 200, "ok", null, 1));
        org.springframework.ai.tool.ToolCallback callback = new AiToolCallbackFactory(executor).build(tool);
        String result = callback.call("{\"city\":\"北京\"}",
            new ToolContext(Map.of(AiToolCallbackFactory.CTX_APP_ID, 7L, AiToolCallbackFactory.CTX_MESSAGE_ID, 8L)));
        assertEquals("ok", result);
    }

    @Test
    void failureReturnsReadableTextInsteadOfThrowing() {
        ToolExecutor executor = mock(ToolExecutor.class);
        AiTool tool = tool();
        when(executor.execute(eq(tool), any(), any())).thenReturn(
            new ToolExecResult(false, 500, null, "HTTP 500: boom", 3));
        org.springframework.ai.tool.ToolCallback callback = new AiToolCallbackFactory(executor).build(tool);
        String result = callback.call("{\"city\":\"北京\"}", null);
        assertEquals("工具调用失败：HTTP 500: boom", result);
    }

    @Test
    void nullSchemaFallsBackToEmptyObject() {
        ToolExecutor executor = mock(ToolExecutor.class);
        AiTool tool = tool();
        tool.setParamSchema(null);
        org.springframework.ai.tool.ToolCallback callback = new AiToolCallbackFactory(executor).build(tool);
        assertEquals("{\"type\":\"object\"}", callback.getToolDefinition().inputSchema());
    }
}
