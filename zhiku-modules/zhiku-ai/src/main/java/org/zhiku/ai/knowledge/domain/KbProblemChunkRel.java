package org.zhiku.ai.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 问题-分段关联对象 kb_problem_chunk_rel（多对多）
 *
 * @author zhiku
 */
@Data
@TableName("kb_problem_chunk_rel")
public class KbProblemChunkRel {

    /**
     * 关联ID
     */
    @TableId(value = "rel_id")
    private Long relId;

    /**
     * 问题ID
     */
    private Long problemId;

    /**
     * 分段ID
     */
    private Long chunkId;

    /**
     * 知识库ID（冗余）
     */
    private Long datasetId;

    /**
     * 文档ID（冗余）
     */
    private Long documentId;

}
