package org.zhiku.ai.model.client;

import org.junit.jupiter.api.Test;
import org.zhiku.ai.model.domain.AiModel;
import org.zhiku.common.core.exception.ServiceException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RerankClient 单元测试（HTTP 层由验收真实端点覆盖，此处覆盖映射纯函数与异常传播）
 *
 * @author zhiku
 */
class RerankClientTest {

    @Test
    void toSortedResultsSortsByScoreDesc() {
        RerankClient.RerankResponse response = new RerankClient.RerankResponse(List.of(
            new RerankClient.RerankResponse.RerankItem(0, 0.31),
            new RerankClient.RerankResponse.RerankItem(1, 0.95),
            new RerankClient.RerankResponse.RerankItem(2, 0.62)
        ));
        List<RerankClient.RerankResult> results = RerankClient.toSortedResults(response);
        assertEquals(3, results.size());
        assertEquals(1, results.get(0).index());
        assertEquals(2, results.get(1).index());
        assertEquals(0, results.get(2).index());
    }

    @Test
    void toSortedResultsNullSafe() {
        assertTrue(RerankClient.toSortedResults(null).isEmpty());
        assertTrue(RerankClient.toSortedResults(new RerankClient.RerankResponse(null)).isEmpty());
    }

    @Test
    void rerankEmptyDocumentsReturnsEmpty() {
        RerankClient client = new RerankClient();
        assertTrue(client.rerank(new AiModel(), "q", List.of()).isEmpty());
    }

    @Test
    void rerankPropagatesServiceException() {
        RerankClient client = new RerankClient() {
            @Override
            protected RerankResponse doPost(AiModel model, java.util.Map<String, Object> body) {
                throw new ServiceException("重排服务调用失败（HTTP 500）");
            }
        };
        AiModel model = new AiModel();
        model.setModelCode("bge-reranker");
        ServiceException ex = assertThrows(ServiceException.class,
            () -> client.rerank(model, "q", List.of("a", "b")));
        assertTrue(ex.getMessage().contains("HTTP 500"));
    }

}
