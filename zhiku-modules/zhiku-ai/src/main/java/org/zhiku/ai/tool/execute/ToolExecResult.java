package org.zhiku.ai.tool.execute;

/**
 * 工具执行结果（body 已按规则截断/包装，可直接回填模型或前端）
 *
 * @author zhiku
 */
public record ToolExecResult(boolean success, Integer httpStatus, String body, String errorMsg, long durationMs) {
}
