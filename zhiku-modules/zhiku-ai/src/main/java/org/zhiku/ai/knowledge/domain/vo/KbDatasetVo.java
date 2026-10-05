package org.zhiku.ai.knowledge.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.zhiku.ai.knowledge.domain.KbDataset;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 知识库视图对象 kb_dataset
 *
 * @author zhiku
 */
@Data
@AutoMapper(target = KbDataset.class)
public class KbDatasetVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 知识库ID
     */
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
     * 绑定的向量模型ID
     */
    private Long embedModelId;

    /**
     * 向量维度
     */
    private Integer embedDim;

    /**
     * 分块参数JSON
     */
    private String chunkParams;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 创建时间
     */
    private Date createTime;

}
