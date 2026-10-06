package org.zhiku.ai.knowledge.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库问题视图对象 kb_problem
 *
 * @author zhiku
 */
@Data
public class KbProblemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 问题ID
     */
    private Long problemId;

    /**
     * 所属知识库ID
     */
    private Long datasetId;

    /**
     * 问题文本
     */
    private String content;

    /**
     * 关联分段ID列表
     */
    private List<Long> chunkIds;

    /**
     * 关联分段数
     */
    private Long chunkCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
