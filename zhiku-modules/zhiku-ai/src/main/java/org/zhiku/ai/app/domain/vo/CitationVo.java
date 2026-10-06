package org.zhiku.ai.app.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 引用溯源条目（SSE citation 帧 / 消息落库共用结构，与 M2 RetrievalHit 映射）
 *
 * @author zhiku
 */
@Data
public class CitationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分块ID
     */
    private Long chunkId;

    /**
     * 文档ID
     */
    private Long documentId;

    /**
     * 文档名
     */
    private String documentName;

    /**
     * 分块序号
     */
    private Integer seq;

    /**
     * 标题路径
     */
    private String headingPath;

    /**
     * 相似度（0-1）
     */
    private Float score;

    /**
     * 原文片段（前 300 字）
     */
    private String snippet;

}
