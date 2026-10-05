package org.zhiku.ai.knowledge.service;

import org.zhiku.ai.knowledge.domain.vo.RetrievalHit;
import org.zhiku.ai.knowledge.domain.vo.RetrievalQuery;

import java.util.List;

/**
 * AI 检索服务（M4 应用对话的唯一检索入口；Milvus 不可用时明确报错，禁止静默空结果）
 *
 * @author zhiku
 */
public interface IAiRetrievalService {

    /**
     * 多知识库检索：按 embedding 模型分组 → 向量召回（并集）→ 可选 Rerank → MySQL 补全 → 过滤禁用分块
     */
    List<RetrievalHit> search(RetrievalQuery query);

}
