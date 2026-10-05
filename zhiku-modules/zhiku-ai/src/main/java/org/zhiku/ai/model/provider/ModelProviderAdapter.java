package org.zhiku.ai.model.provider;

import org.zhiku.ai.model.domain.AiModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * 模型供应商适配器（策略模式）：把 AiModel 配置构建为 Spring AI 客户端
 * <p>新增供应商 = 新实现类，分发逻辑零改动（对齐仓库 IAuthStrategy 先例）。
 *
 * @author zhiku
 */
public interface ModelProviderAdapter {

    /**
     * 是否支持该供应商标识
     */
    boolean supports(String provider);

    /**
     * 构建 ChatModel（LLM 对话模型）
     */
    ChatModel buildChatModel(AiModel model);

    /**
     * 构建 EmbeddingModel（向量化模型）
     */
    EmbeddingModel buildEmbeddingModel(AiModel model);

}
