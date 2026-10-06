package org.zhiku.ai.app.runner;

import org.springframework.ai.chat.model.ChatResponse;

import java.util.function.Consumer;

/**
 * 流式帧提取器：从 Flux&lt;ChatResponse&gt; 逐条提取文本增量与 token 用量（纯消费逻辑可单测）
 *
 * @author zhiku
 */
public class ChatStreamFrameExtractor {

    /**
     * 处理单个 ChatResponse：非空文本追加到 content 并回调 onDelta；返回本条 token 用量（无则 null）
     *
     * @return [promptTokens, completionTokens]，本条无 usage 时为 null
     */
    public Integer[] handle(ChatResponse resp, StringBuilder content, Consumer<String> onDelta) {
        if (resp == null) {
            return null;
        }
        String text = resp.getResult() == null || resp.getResult().getOutput() == null
            ? null : resp.getResult().getOutput().getText();
        if (text != null && !text.isEmpty()) {
            content.append(text);
            onDelta.accept(text);
        }
        if (resp.getMetadata() != null && resp.getMetadata().getUsage() != null) {
            var usage = resp.getMetadata().getUsage();
            return new Integer[]{usage.getPromptTokens(), usage.getCompletionTokens()};
        }
        return null;
    }

}
