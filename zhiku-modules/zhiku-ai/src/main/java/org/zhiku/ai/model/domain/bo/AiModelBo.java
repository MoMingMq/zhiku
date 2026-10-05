package org.zhiku.ai.model.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.zhiku.ai.model.domain.AiModel;
import org.zhiku.common.core.validate.AddGroup;
import org.zhiku.common.core.validate.EditGroup;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 模型配置业务对象 ai_model
 *
 * @author zhiku
 */
@Data
@AutoMapper(target = AiModel.class, reverseConvertGenerate = false)
public class AiModelBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模型ID
     */
    @NotNull(message = "模型ID不能为空", groups = { EditGroup.class })
    private Long modelId;

    /**
     * 模型名称
     */
    @NotBlank(message = "模型名称不能为空", groups = { AddGroup.class, EditGroup.class })
    @Size(min = 0, max = 100, message = "模型名称不能超过{max}个字符")
    private String modelName;

    /**
     * 模型类型（1LLM对话 2Embedding向量 3Rerank重排）
     */
    @NotBlank(message = "模型类型不能为空", groups = { AddGroup.class, EditGroup.class })
    private String modelType;

    /**
     * 供应商标识
     */
    private String provider;

    /**
     * API 地址
     */
    @NotBlank(message = "API地址不能为空", groups = { AddGroup.class, EditGroup.class })
    private String baseUrl;

    /**
     * 模型标识
     */
    @NotBlank(message = "模型标识不能为空", groups = { AddGroup.class, EditGroup.class })
    @Size(min = 0, max = 100, message = "模型标识不能超过{max}个字符")
    private String modelCode;

    /**
     * API Key（编辑时留空表示不修改）
     */
    @Size(min = 0, max = 1000, message = "API Key不能超过{max}个字符")
    private String apiKey;

    /**
     * 向量维度（模型类型=2 时必填）
     */
    private Integer embedDim;

    /**
     * 默认参数JSON
     */
    private String params;

    /**
     * 是否默认模型（0否 1是）
     */
    private String isDefault;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

}
