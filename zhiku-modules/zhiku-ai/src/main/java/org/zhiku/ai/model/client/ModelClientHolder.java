package org.zhiku.ai.model.client;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * 已构建的模型客户端持有对（按 modelType 只构建对应侧，另一侧为 null）
 *
 * @author zhiku
 */
public record ModelClientHolder(ChatModel chatModel, EmbeddingModel embeddingModel) {
}
