package org.zhiku.ai.app.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.zhiku.ai.app.domain.AiApp;
import org.zhiku.common.core.validate.AddGroup;
import org.zhiku.common.core.validate.EditGroup;

import java.io.Serial;
import java.io.Serializable;

/**
 * 应用业务对象 ai_app
 *
 * @author zhiku
 */
@Data
@AutoMapper(target = AiApp.class, reverseConvertGenerate = false)
public class AiAppBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应用ID（编辑必填）
     */
    @NotNull(message = "应用ID不能为空", groups = { EditGroup.class })
    private Long appId;

    /**
     * 应用名称
     */
    @NotBlank(message = "应用名称不能为空", groups = { AddGroup.class, EditGroup.class })
    @Size(min = 1, max = 100, message = "应用名称不能超过{max}个字符")
    private String appName;

    /**
     * 描述
     */
    @Size(min = 0, max = 500, message = "描述不能超过{max}个字符")
    private String description;

    /**
     * 图标
     */
    private String icon;

    /**
     * 应用类型（simple/workflow）
     */
    @NotBlank(message = "应用类型不能为空", groups = { AddGroup.class })
    private String appType;

    /**
     * 对话类型码
     */
    private String chatType;

    /**
     * 对话类型专属配置 JSON
     */
    private String config;

    /**
     * 对话模型ID
     */
    @NotNull(message = "必须选择对话模型", groups = { AddGroup.class, EditGroup.class })
    private Long modelId;

    /**
     * 模型参数覆盖 JSON
     */
    private String modelParams;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 开场白
     */
    private String prologue;

    /**
     * 推荐问题 JSON 数组
     */
    private String suggestedQuestions;

    /**
     * 状态（0启用 1停用）
     */
    private String status;

    /**
     * 查询条件：名称模糊
     */
    private String appNameLike;

}
