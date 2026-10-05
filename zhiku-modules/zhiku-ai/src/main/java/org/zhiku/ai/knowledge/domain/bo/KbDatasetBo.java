package org.zhiku.ai.knowledge.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.zhiku.ai.knowledge.domain.KbDataset;
import org.zhiku.common.core.validate.AddGroup;
import org.zhiku.common.core.validate.EditGroup;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识库业务对象 kb_dataset
 *
 * @author zhiku
 */
@Data
@AutoMapper(target = KbDataset.class, reverseConvertGenerate = false)
public class KbDatasetBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 知识库ID
     */
    @NotNull(message = "知识库ID不能为空", groups = { EditGroup.class })
    private Long datasetId;

    /**
     * 知识库名称
     */
    @NotBlank(message = "知识库名称不能为空", groups = { AddGroup.class, EditGroup.class })
    @Size(min = 0, max = 100, message = "知识库名称不能超过{max}个字符")
    private String datasetName;

    /**
     * 描述
     */
    @Size(min = 0, max = 500, message = "描述不能超过{max}个字符")
    private String description;

    /**
     * 绑定的向量模型ID（维度由模型侧带出，不在此填写）
     */
    @NotNull(message = "必须选择向量模型", groups = { AddGroup.class, EditGroup.class })
    private Long embedModelId;

    /**
     * 分块参数JSON
     */
    private String chunkParams;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

}
