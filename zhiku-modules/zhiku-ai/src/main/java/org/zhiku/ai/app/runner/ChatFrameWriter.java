package org.zhiku.ai.app.runner;

import lombok.extern.slf4j.Slf4j;
import org.zhiku.common.json.utils.JsonUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * SSE 语义帧写出器（六类事件：citation/delta/tool_call/tool_result/done/error；done/error 互斥终帧）
 *
 * @author zhiku
 */
@Slf4j
public class ChatFrameWriter {

    private final SseEmitter emitter;

    public ChatFrameWriter(SseEmitter emitter) {
        this.emitter = emitter;
    }

    /**
     * 引用溯源帧（检索完成后、首 token 前）
     */
    public void citation(Object citations) {
        send("citation", Map.of("citations", citations));
    }

    /**
     * 文本增量帧
     */
    public void delta(String content) {
        send("delta", Map.of("content", content));
    }

    /**
     * 工具调用帧（模型决策调用时）
     */
    public void toolCall(String toolName, String args) {
        send("tool_call", Map.of("toolName", toolName, "args", args));
    }

    /**
     * 工具结果帧（result 截 500 字）
     */
    public void toolResult(String toolName, boolean success, String result) {
        send("tool_result", Map.of("toolName", toolName, "success", success,
            "result", result == null ? "" : (result.length() > 500 ? result.substring(0, 500) : result)));
    }

    /**
     * 终帧（正常完成）
     */
    public void done(Long messageId, Long conversationId, Integer promptTokens, Integer completionTokens) {
        send("done", Map.of("messageId", messageId == null ? 0 : messageId,
            "conversationId", conversationId == null ? 0 : conversationId,
            "promptTokens", promptTokens == null ? 0 : promptTokens,
            "completionTokens", completionTokens == null ? 0 : completionTokens));
    }

    /**
     * 终帧（异常；retryable=true 客户端可重试）
     */
    public void error(String message, boolean retryable) {
        send("error", Map.of("message", message, "retryable", retryable));
    }

    private void send(String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(JsonUtils.toJsonString(data)));
        } catch (Exception e) {
            // 客户端断开/超时：静默（上层 onError 回调负责 dispose 与落库）
            log.debug("SSE 帧发送失败（客户端可能已断开）: event={}", event);
        }
    }

}
