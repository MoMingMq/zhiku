package org.zhiku.ai.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.zhiku.common.mybatis.core.domain.BaseEntity;

/**
 * 知识库对象 kb_dataset
 *
 * @author zhiku
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_dataset")
public class KbDataset extends BaseEntity {

    /**
     * 知识库ID
     */
    @TableId(value = "dataset_id")
    private Long datasetId;

    /**
     * 知识库名称
     */
    private String datasetName;

    /**
     * 描述
     */
    private String description;

    /**
     * 绑定的向量模型ID（ai_model）
     */
    private Long embedModelId;

    /**
     * 向量维度（冗余，Milvus collection 路由用）
     */
    private Integer embedDim;

    /**
     * 分块参数JSON（mode/maxChars/overlapRatio/semanticThreshold）
     */
    private String chunkParams;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 删除标志（0代表存在 2代表删除）
     */
    @TableLogic
    private String delFlag;

}
