package org.zhiku.ai.tool.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.zhiku.common.encrypt.annotation.EncryptField;
import org.zhiku.common.mybatis.core.domain.BaseEntity;

/**
 * AI 工具对象 ai_tool
 *
 * @author zhiku
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_tool")
public class AiTool extends BaseEntity {

    /**
     * 工具ID
     */
    @TableId(value = "tool_id")
    private Long toolId;

    /**
     * 工具名（唯一，function calling 名称，字母开头字母数字下划线连字符）
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
     * URL 模板（scheme+host 禁止参数占位）
     */
    private String urlTemplate;

    /**
     * 请求头 JSON 数组 [{key,value}]，value 支持 {{参数}}
     */
    private String headers;

    /**
     * 查询参数 JSON 数组 [{key,value}]，value 支持 {{参数}}
     */
    private String queryParams;

    /**
     * 请求体模板，支持 {{参数}}
     */
    private String bodyTemplate;

    /**
     * 入参 JSON Schema（根必须 object，即 Function Calling parameters）
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
     * 鉴权凭据（加密存储，出口掩码）
     */
    @EncryptField
    private String authValue;

    /**
     * 允许内网目标（0否 1是，SSRF 白名单）
     */
    private String allowIntranet;

    /**
     * 超时秒数（connect/read 共用，上限60）
     */
    private Integer timeoutSeconds;

    /**
     * 状态（0启用 1停用）
     */
    private String status;

    /**
     * 删除标志（0存在 2删除）
     */
    @TableLogic
    private String delFlag;

}
