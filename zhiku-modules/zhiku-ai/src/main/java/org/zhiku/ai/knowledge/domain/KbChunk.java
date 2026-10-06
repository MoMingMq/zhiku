package org.zhiku.ai.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.zhiku.common.mybatis.core.domain.BaseEntity;

/**
 * 知识库分块对象 kb_chunk（由入库管线构造，无独立 Bo）
 *
 * @author zhiku
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_chunk")
public class KbChunk extends BaseEntity {

    /**
     * 分块ID
     */
    @TableId(value = "chunk_id")
    private Long chunkId;

    /**
     * 所属知识库ID（冗余，检索过滤用）
     */
    private Long datasetId;

    /**
     * 所属文档ID
     */
    private Long documentId;

    /**
     * 文档内序号
     */
    private Integer seq;

    /**
     * 分块正文
     */
    private String content;

    /**
     * 分段标题（默认取标题路径末级，可编辑）
     */
    private String title;

    /**
     * 标题路径（如 第一章>1.1）
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
     * 删除标志（0代表存在 2代表删除）
     */
    @TableLogic
    private String delFlag;

}
