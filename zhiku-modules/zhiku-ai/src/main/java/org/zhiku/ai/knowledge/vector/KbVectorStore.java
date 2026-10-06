package org.zhiku.ai.knowledge.vector;

import lombok.Data;

import java.util.List;

/**
 * 向量库抽象（collection 按 embedding 维度分：zhiku_kb_chunk_{dim}；换库 = 新实现类）
 *
 * @author zhiku
 */
public interface KbVectorStore {

    /**
     * 确保维度对应 collection 存在（幂等：不存在则建 schema+索引）
     */
    void ensureCollection(int dim);

    /**
     * 批量写入（upsert，id 为雪花串）
     */
    void upsert(int dim, List<VectorRow> rows);

    /**
     * 相似度检索；filter 形如 "kb_id in [1,2]"
     */
    List<VectorHit> search(int dim, float[] queryVector, String filter, int topK);

    /**
     * 表达式删除，如 "document_id == 123"
     */
    void deleteByExpr(int dim, String expr);

    /**
     * 按表达式计数（对账用：与 kb_chunk 计数核对）
     */
    long count(int dim, String filter);

    /**
     * 写入行
     */
    @Data
    class VectorRow {
        private String id;
        private float[] vector;
        private Long kbId;
        private Long documentId;
        private Long chunkId;

        public static VectorRow of(String id, float[] vector, Long kbId, Long documentId, Long chunkId) {
            VectorRow row = new VectorRow();
            row.id = id;
            row.vector = vector;
            row.kbId = kbId;
            row.documentId = documentId;
            row.chunkId = chunkId;
            return row;
        }
    }

    /**
     * 检索命中（score 越大越相似，COSINE）
     */
    @Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class VectorHit {
        private String id;
        private Long kbId;
        private Long documentId;
        private Long chunkId;
        private float score;
    }

}
