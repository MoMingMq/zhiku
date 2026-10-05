package org.zhiku.ai.knowledge.domain.vo;

import lombok.Data;

/**
 * 检索命中（citation 的数据来源：来源文档 + 位置 + 原文片段 + 相似度）
 *
 * @author zhiku
 */
@Data
public class RetrievalHit {

    private Long chunkId;

    private Long documentId;

    private String documentName;

    private String content;

    private String headingPath;

    private Integer seq;

    private float score;

    private Long datasetId;

}
