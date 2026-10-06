package org.zhiku.ai.tool.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.zhiku.ai.tool.domain.AiTool;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * AI 工具视图对象 ai_tool
 *
 * @author zhiku
 */
@Data
@AutoMapper(target = AiTool.class)
public class AiToolVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 工具ID
     */
    private Long toolId;

    /**
     * 工具名
     */
    private String toolName;

    /**
     * 给模型看的语义说明
     */
    private String description;

    /**
     * 图标（emoji）
     */
    private String icon;

    /**
     * 请求方式（GET/POST/PUT/DELETE）
     */
    private String method;

    /**
     * URL 模板
     */
    private String urlTemplate;

    /**
     * 请求头 JSON 数组
     */
    private String headers;

    /**
     * 查询参数 JSON 数组
     */
    private String queryParams;

    /**
     * 请求体模板
     */
    private String bodyTemplate;

    /**
     * 入参 JSON Schema
     */
    private String paramSchema;

    /**
     * 鉴权类型（0无 1Bearer 2自定义头）
     */
    private String authType;

    /**
     * 自定义鉴权头名
     */
    private String authKey;

    /**
     * 鉴权凭据掩码（如 ***，永不回显明文；空=未配置）
     */
    private String authValue;

    /**
     * 是否已配置鉴权凭据
     */
    private Boolean authValueSet;

    /**
     * 允许内网目标（0否 1是）
     */
    private String allowIntranet;

    /**
     * 超时秒数
     */
    private Integer timeoutSeconds;

    /**
     * 状态（0启用 1停用）
     */
    private String status;

    /**
     * 创建时间
     */
    private Date createTime;

}
