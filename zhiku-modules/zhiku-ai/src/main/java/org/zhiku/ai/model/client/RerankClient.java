package org.zhiku.ai.model.client;

import lombok.extern.slf4j.Slf4j;
import org.zhiku.ai.model.domain.AiModel;
import org.zhiku.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Rerank 重排客户端（Cohere 兼容 /v1/rerank：SiliconFlow、Jina、vLLM 通用格式）
 * <p>请求体：{model, query, documents, top_n, return_documents}；
 * 响应体：{results: [{index, relevance_score}]}，按 relevance_score 降序返回。
 *
 * @author zhiku
 */
@Slf4j
@Component
public class RerankClient {

    /**
     * 重排结果（index 为候选文档原下标）
     */
    public record RerankResult(int index, double score) {
    }

    /**
     * Cohere 兼容响应结构
     */
    public record RerankResponse(List<RerankItem> results) {
        public record RerankItem(int index, double relevance_score) {
        }
    }

    /**
     * 对候选文档重排
     *
     * @param model     rerank 模型配置（modelType=3）
     * @param query     查询文本
     * @param documents 候选文档列表
     * @return 按相关性降序的 (原下标, 分数)，空列表安全返回空结果
     */
    public List<RerankResult> rerank(AiModel model, String query, List<String> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        Map<String, Object> body = Map.of(
            "model", model.getModelCode(),
            "query", query,
            "documents", documents,
            "top_n", documents.size(),
            "return_documents", false);
        RerankResponse response = doPost(model, body);
        return toSortedResults(response);
    }

    /**
     * 响应映射为降序结果（纯函数，可单测）
     */
    static List<RerankResult> toSortedResults(RerankResponse response) {
        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results().stream()
            .map(item -> new RerankResult(item.index(), item.relevance_score()))
            .sorted(Comparator.comparingDouble(RerankResult::score).reversed())
            .toList();
    }

    /**
     * 执行 HTTP 调用（protected：单测可覆写，真实链路由验收覆盖）
     */
    protected RerankResponse doPost(AiModel model, Map<String, Object> body) {
        try {
            return RestClient.create()
                .post()
                .uri(model.getBaseUrl() + "/rerank")
                .header("Authorization", "Bearer " + model.getApiKey())
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(RerankResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("rerank 调用失败 status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ServiceException("重排服务调用失败（HTTP " + e.getStatusCode().value() + "）");
        } catch (Exception e) {
            log.warn("rerank 调用异常", e);
            throw new ServiceException("重排服务不可达：" + e.getMessage());
        }
    }

}
