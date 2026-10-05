package org.zhiku.ai.model.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.zhiku.ai.model.domain.AiModel;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 模型配置视图对象 ai_model
 *
 * @author zhiku
 */
@Data
@AutoMapper(target = AiModel.class)
public class AiModelVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模型ID
     */
    private Long modelId;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 模型类型（1LLM对话 2Embedding向量 3Rerank重排）
     */
    private String modelType;

    /**
     * 供应商标识
     */
    private String provider;

    /**
     * API 地址
     */
    private String baseUrl;

    /**
     * 模型标识
     */
    private String modelCode;

    /**
     * API Key 掩码（如 sk-***，永不回显明文）
     */
    private String apiKey;

    /**
     * 是否已配置 API Key
     */
    private Boolean hasKey;

    /**
     * 向量维度
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

    /**
     * 创建时间
     */
    private java.util.Date createTime;

}
