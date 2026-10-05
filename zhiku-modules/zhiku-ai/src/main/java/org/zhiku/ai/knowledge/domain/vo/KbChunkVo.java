package org.zhiku.ai.knowledge.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.zhiku.ai.knowledge.domain.KbChunk;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 知识库分块视图对象 kb_chunk
 *
 * @author zhiku
 */
@Data
@AutoMapper(target = KbChunk.class)
public class KbChunkVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分块ID
     */
    private Long chunkId;

    /**
     * 所属文档ID
     */
    private Long documentId;

    /**
     * 所属知识库ID
     */
    private Long datasetId;

    /**
     * 文档内序号
     */
    private Integer seq;

    /**
     * 分块正文
     */
    private String content;

    /**
     * 标题路径
     */
    private String headingPath;

    /**
     * 产出标记（structure/semantic/recursive）
     */
    private String strategyTag;

    /**
     * 字符数
     */
    private Integer charCount;

    /**
     * 禁用（1禁用后不参与检索）
     */
    private String disabled;

    /**
     * 创建时间
     */
    private Date createTime;

}
