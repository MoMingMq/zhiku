package org.zhiku.ai.model.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.zhiku.common.encrypt.annotation.EncryptField;
import org.zhiku.common.mybatis.core.domain.BaseEntity;

/**
 * AI 模型配置对象 ai_model
 *
 * @author zhiku
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model")
public class AiModel extends BaseEntity {

    /**
     * 模型ID
     */
    @TableId(value = "model_id")
    private Long modelId;

    /**
     * 模型名称（显示用）
     */
    private String modelName;

    /**
     * 模型类型（1LLM对话 2Embedding向量 3Rerank重排）
     */
    private String modelType;

    /**
     * 供应商标识（openai_compatible 兼容 DeepSeek/通义兼容端点/Ollama 等）
     */
    private String provider;

    /**
     * API 地址
     */
    private String baseUrl;

    /**
     * 模型标识（如 deepseek-chat）
     */
    private String modelCode;

    /**
     * API Key（加密存储）
     */
    @EncryptField
    private String apiKey;

    /**
     * 向量维度（模型类型=2 时必填）
     */
    private Integer embedDim;

    /**
     * 默认参数JSON（temperature/maxTokens/topP）
     */
    private String params;

    /**
     * 上下文长度上限（token，空=未知按默认预算）
     */
    private Integer contextWindow;

    /**
     * 最大输出 token（空=不限制）
     */
    private Integer maxOutputTokens;

    /**
     * 是否默认模型（0否 1是，每类型至多一个）
     */
    private String isDefault;

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
