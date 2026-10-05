package org.zhiku.ai.model.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zhiku.ai.model.domain.AiModel;
import org.zhiku.ai.model.mapper.AiModelMapper;
import org.zhiku.ai.model.provider.ModelProviderAdapter;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型客户端工厂：按 AiModel 配置构建并缓存 Spring AI 客户端
 * <p>直接走 Mapper 取原始配置（apiKey 加密列经 MyBatis-Plus 拦截器自动解密），
 * 不走 Service（Vo 层 apiKey 已掩码）。配置变更/删除时由 Service 侧调用 {@link #evict(Long)} 失效。
 * <p>契约签名（M2/M4 消费）：getEmbeddingModel(Long) / getChatModel(Long) / evict(Long)。
 *
 * @author zhiku
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class AiModelClientFactory {

    private final List<ModelProviderAdapter> adapters;
    private final AiModelMapper modelMapper;

    /**
     * modelId → 已构建客户端（单体本地缓存足够；换库/重启即重建）
     */
    private final Map<Long, ModelClientHolder> cache = new ConcurrentHashMap<>();

    /**
     * 获取 EmbeddingModel（modelType=2）
     */
    public EmbeddingModel getEmbeddingModel(Long modelId) {
        ModelClientHolder holder = holder(modelId);
        if (holder.embeddingModel() == null) {
            throw new IllegalArgumentException("模型 " + modelId + " 不是 Embedding 类型");
        }
        return holder.embeddingModel();
    }

    /**
     * 获取 ChatModel（modelType=1）
     */
    public ChatModel getChatModel(Long modelId) {
        ModelClientHolder holder = holder(modelId);
        if (holder.chatModel() == null) {
            throw new IllegalArgumentException("模型 " + modelId + " 不是 LLM 对话类型");
        }
        return holder.chatModel();
    }

    /**
     * 配置变更/删除后失效缓存（由 AiModelServiceImpl 的 update/delete 调用）
     */
    public void evict(Long modelId) {
        cache.remove(modelId);
    }

    private ModelClientHolder holder(Long modelId) {
        return cache.computeIfAbsent(modelId, id -> {
            AiModel model = modelMapper.selectById(id);
            if (model == null) {
                throw new IllegalArgumentException("模型不存在：" + id);
            }
            ModelProviderAdapter adapter = adapters.stream()
                .filter(a -> a.supports(model.getProvider()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的模型供应商：" + model.getProvider()));
            boolean isEmbedding = "2".equals(model.getModelType());
            if ("3".equals(model.getModelType())) {
                throw new IllegalArgumentException("Rerank 模型不走客户端工厂，请使用 RerankClient");
            }
            return new ModelClientHolder(
                isEmbedding ? null : adapter.buildChatModel(model),
                isEmbedding ? adapter.buildEmbeddingModel(model) : null);
        });
    }

}
