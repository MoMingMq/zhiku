package org.zhiku.ai.knowledge.vector;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MilvusKbVectorStore 集成测试（真实 Milvus 服务闭环）
 * <p>门控：MILVUS_IT=1 时执行（默认跳过）；结束后清理测试 collection。
 *
 * @author zhiku
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "MILVUS_IT", matches = "1")
class MilvusKbVectorStoreIT {

    /** 测试用维度（避开生产 embedding 维度） */
    private static final int DIM = 4;

    private static final MilvusProperties PROPS = props();

    private final MilvusClientHolder holder = new MilvusClientHolder();

    /** 简单客户端持有（仅为测试生命周期管理） */
    static class MilvusClientHolder implements AutoCloseable {
        io.milvus.v2.client.MilvusClientV2 client;
        MilvusKbVectorStore store;

        MilvusClientHolder() {
            client = MilvusKbVectorStore.createClient(PROPS);
            store = new MilvusKbVectorStore(client, PROPS.getDatabase());
        }

        @Override
        public void close() {
            client.close();
        }
    }

    private static MilvusProperties props() {
        MilvusProperties properties = new MilvusProperties();
        properties.setEnabled(true);
        properties.setUri(System.getenv().getOrDefault("MILVUS_URI", "http://127.0.0.1:19530"));
        properties.setDatabase("default");
        return properties;
    }

    @Test
    @Order(1)
    void ensureCollectionThenUpsertSearchDeleteCount() {
        try (MilvusClientHolder h = new MilvusClientHolder()) {
            KbVectorStore store = h.store;
            // 前置清理：历史失败运行可能残留试数据（kb_id 1/2 为测试专用段）
            store.deleteByExpr(DIM, "kb_id in [1, 2]");
            // 1. 建集合（幂等）
            store.ensureCollection(DIM);
            store.ensureCollection(DIM);
            // 2. 写 3 条（同向量的近似数据：kb 1 文档 100 两条 + kb 2 文档 200 一条）
            store.upsert(DIM, List.of(
                KbVectorStore.VectorRow.of("900001", new float[]{1, 0, 0, 0}, 1L, 100L, 1001L),
                KbVectorStore.VectorRow.of("900002", new float[]{0.9f, 0.1f, 0, 0}, 1L, 100L, 1002L),
                KbVectorStore.VectorRow.of("900003", new float[]{0, 0, 0, 1}, 2L, 200L, 1003L)));
            // 3. 检索：query 与 kb1 向量近，过滤 kb_id in [1,2] 取 top2
            List<KbVectorStore.VectorHit> hits = store.search(DIM, new float[]{1, 0, 0, 0},
                "kb_id in [1,2]", 2);
            assertEquals(2, hits.size(), "top2 应返回两条");
            assertTrue(hits.get(0).getScore() >= hits.get(1).getScore(), "分数降序");
            assertEquals(100L, hits.get(0).getDocumentId(), "最相似命中 kb1 文档");
            // 4. 表达式删除 + 计数
            store.deleteByExpr(DIM, "document_id == 100");
            assertEquals(0, store.count(DIM, "document_id == 100"), "删除后无残留");
            assertEquals(1, store.count(DIM, "kb_id == 2"), "kb2 记录保留");
            // 5. 清理：删 kb2 残试数据
            store.deleteByExpr(DIM, "kb_id == 2");
            assertEquals(0, store.count(DIM, ""), "试数据清零");
        }
    }

    @Test
    @Order(2)
    void searchWithNarrowFilter() {
        try (MilvusClientHolder h = new MilvusClientHolder()) {
            KbVectorStore store = h.store;
            store.ensureCollection(DIM);
            store.deleteByExpr(DIM, "kb_id in [1, 2]");
            store.upsert(DIM, List.of(
                KbVectorStore.VectorRow.of("900011", new float[]{1, 0, 0, 0}, 1L, 110L, 1101L),
                KbVectorStore.VectorRow.of("900012", new float[]{1, 0, 0, 0}, 2L, 220L, 2202L)));
            // 同向量不同库：filter 裁剪只应命中 kb2
            List<KbVectorStore.VectorHit> hits = store.search(DIM, new float[]{1, 0, 0, 0},
                "kb_id == 2", 5);
            assertEquals(1, hits.size());
            assertEquals(2L, hits.get(0).getKbId());
            store.deleteByExpr(DIM, "document_id == 110");
            store.deleteByExpr(DIM, "document_id == 220");
        }
    }

}
