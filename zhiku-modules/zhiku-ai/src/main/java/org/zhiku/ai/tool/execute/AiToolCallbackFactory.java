package org.zhiku.ai.tool.execute;

import lombok.RequiredArgsConstructor;
import org.zhiku.ai.tool.domain.AiTool;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Spring AI 工具回调工厂：ai_tool 配置 → FunctionToolCallback（模块内 Spring AI 唯一耦合点）
 * <p>MCP 预留锚点：后续把工具经 common-mcp 的 @McpTool 对外暴露时，
 * 实现体直接复用 ToolExecutor（调业务组件，不直访 Mapper）。
 *
 * @author zhiku
 */
@Component
@RequiredArgsConstructor
public class AiToolCallbackFactory {

    /**
     * ToolContext 审计回填键（M4 发起对话时写入，executor 取值填 ai_tool_call_log）
     */
    public static final String CTX_APP_ID = "zhiku.appId";
    public static final String CTX_MESSAGE_ID = "zhiku.messageId";

    private final ToolExecutor toolExecutor;

    /**
     * 包装单工具：name=工具名、description=配置描述、inputSchema=入参 JSON Schema 原文
     * <p>结果转换器：执行器返回的已是文本（含截断/错误可读包装），String 原样返回——
     * 2.0 默认转换器会对一切结果 JSON 序列化（String 会多出一层引号，模型读到转义文本）。
     */
    public ToolCallback build(AiTool tool) {
        return FunctionToolCallback.builder(tool.getToolName(),
                (Map<String, Object> params, ToolContext ctx) -> executeForChat(tool, params, ctx))
            .description(tool.getDescription())
            .inputSchema(tool.getParamSchema() == null ? "{\"type\":\"object\"}" : tool.getParamSchema())
            .inputType(Map.class)
            .toolCallResultConverter((result, type) -> result instanceof String text ? text : String.valueOf(result))
            .build();
    }

    private String executeForChat(AiTool tool, Map<String, Object> params, ToolContext ctx) {
        Long appId = ctx == null ? null : (Long) ctx.getContext().get(CTX_APP_ID);
        Long messageId = ctx == null ? null : (Long) ctx.getContext().get(CTX_MESSAGE_ID);
        ToolExecResult result = toolExecutor.execute(tool, params, ToolCallContext.chat(appId, messageId));
        // 失败也返回可读文本：模型能向用户解释失败原因（不抛异常打断对话流）
        return result.success() ? result.body() : ("工具调用失败：" + result.errorMsg());
    }
}
