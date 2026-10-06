package org.zhiku.ai.tool.execute;

/**
 * 工具调用上下文（区分试运行/对话；对话时携带审计回填键）
 *
 * @author zhiku
 */
public record ToolCallContext(String scene, Long appId, Long messageId, Long operatorId) {

    /**
     * 场景：试运行
     */
    public static ToolCallContext testRun(Long operatorId) {
        return new ToolCallContext("0", null, null, operatorId);
    }

    /**
     * 场景：对话调用（M4 发起，appId/messageId 来自 ToolContext）
     */
    public static ToolCallContext chat(Long appId, Long messageId) {
        return new ToolCallContext("1", appId, messageId, null);
    }
}
