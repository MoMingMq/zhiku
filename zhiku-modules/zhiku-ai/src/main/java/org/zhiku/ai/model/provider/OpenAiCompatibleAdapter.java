package org.zhiku.ai.model.provider;

import org.zhiku.ai.model.domain.AiModel;
import org.zhiku.common.core.utils.StringUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容格式适配器（Spring AI 2.0：连接信息随 options 携带，构建时由 OpenAiSetup 自动装配客户端）
 * <p>覆盖 DeepSeek / 通义兼容端点 / Ollama / SiliconFlow 等一切 OpenAI 兼容服务，
 * 仅 baseUrl 与模型标识不同。
 *
 * @author zhiku
 */
@Component
public class OpenAiCompatibleAdapter implements ModelProviderAdapter {

    /**
     * OpenAI 兼容供应商标识
     */
    public static final String PROVIDER = "openai_compatible";

    /**
     * 无 Key 服务（如本地 Ollama）的占位 Key：Spring AI 要求至少一个 credential
     */
    private static final String DUMMY_KEY = "sk-no-auth";

    @Override
    public boolean supports(String provider) {
        return PROVIDER.equals(provider);
    }

    @Override
    public ChatModel buildChatModel(AiModel model) {
        return OpenAiChatModel.builder()
            .options(OpenAiChatOptions.builder()
                .model(model.getModelCode())
                .baseUrl(model.getBaseUrl())
                .apiKey(resolveKey(model))
                .temperature(AiModelParamsHelper.temperature(model.getParams()))
                .maxTokens(AiModelParamsHelper.maxTokens(model.getParams()))
                .topP(AiModelParamsHelper.topP(model.getParams()))
                .build())
            .build();
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(AiModel model) {
        return OpenAiEmbeddingModel.builder()
            .options(OpenAiEmbeddingOptions.builder()
                .model(model.getModelCode())
                .baseUrl(model.getBaseUrl())
                .apiKey(resolveKey(model))
                .build())
            .build();
    }

    private String resolveKey(AiModel model) {
        return StringUtils.isBlank(model.getApiKey()) ? DUMMY_KEY : model.getApiKey();
    }

}
