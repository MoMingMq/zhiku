package org.zhiku.ai.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.zhiku.common.mybatis.core.domain.BaseEntity;

/**
 * 知识库问题对象 kb_problem（问题文本向量化参与检索，命中后展开到关联分段）
 *
 * @author zhiku
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_problem")
public class KbProblem extends BaseEntity {

    /**
     * 问题ID
     */
    @TableId(value = "problem_id")
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
     * 删除标志（0代表存在 2代表删除）
     */
    @TableLogic
    private String delFlag;

}
