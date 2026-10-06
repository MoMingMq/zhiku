package org.zhiku.ai.tool.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.zhiku.ai.tool.domain.AiTool;
import org.zhiku.common.core.validate.AddGroup;
import org.zhiku.common.core.validate.EditGroup;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工具业务对象 ai_tool
 *
 * @author zhiku
 */
@Data
@AutoMapper(target = AiTool.class, reverseConvertGenerate = false)
public class AiToolBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 工具ID（编辑必填）
     */
    @NotNull(message = "工具ID不能为空", groups = { EditGroup.class })
    private Long toolId;

    /**
     * 工具名（唯一，字母开头，可含数字下划线连字符）
     */
    @NotBlank(message = "工具名不能为空", groups = { AddGroup.class, EditGroup.class })
    @Size(min = 1, max = 64, message = "工具名不能超过{max}个字符")
    private String toolName;

    /**
     * 给模型看的语义说明（描述质量决定模型调用准确度）
     */
    @NotBlank(message = "描述不能为空（描述质量决定模型调用准确度）", groups = { AddGroup.class, EditGroup.class })
    @Size(min = 1, max = 500, message = "描述不能超过{max}个字符")
    private String description;

    /**
     * 图标（emoji）
     */
    @Size(min = 0, max = 32, message = "图标不能超过{max}个字符")
    private String icon;

    /**
     * 请求方式（GET/POST/PUT/DELETE）
     */
    @NotBlank(message = "请求方式不能为空", groups = { AddGroup.class, EditGroup.class })
    private String method;

    /**
     * URL 模板（scheme+host 禁止参数占位）
     */
    @NotBlank(message = "URL 不能为空", groups = { AddGroup.class, EditGroup.class })
    @Size(min = 1, max = 1000, message = "URL 不能超过{max}个字符")
    private String urlTemplate;

    /**
     * 请求头 JSON 数组 [{key,value}]
     */
    private String headers;

    /**
     * 查询参数 JSON 数组 [{key,value}]
     */
    private String queryParams;

    /**
     * 请求体模板
     */
    private String bodyTemplate;

    /**
     * 入参 JSON Schema（根必须 object）
     */
    private String paramSchema;

    /**
     * 鉴权类型（0无 1Bearer 2自定义头）
     */
    private String authType;

    /**
     * 自定义鉴权头名（auth_type=2 必填）
     */
    private String authKey;

    /**
     * 鉴权凭据（新增可选；编辑留空=不修改）
     */
    @Size(min = 0, max = 512, message = "鉴权凭据不能超过{max}个字符")
    private String authValue;

    /**
     * 允许内网目标（0否 1是，SSRF 白名单）
     */
    private String allowIntranet;

    /**
     * 超时秒数（1-60）
     */
    private Integer timeoutSeconds;

    /**
     * 状态（0启用 1停用）
     */
    private String status;

}
