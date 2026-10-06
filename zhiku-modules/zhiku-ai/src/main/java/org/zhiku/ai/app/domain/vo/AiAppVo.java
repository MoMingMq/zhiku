package org.zhiku.ai.app.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.zhiku.ai.app.domain.AiApp;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * AI 应用视图对象 ai_app
 *
 * @author zhiku
 */
@Data
@AutoMapper(target = AiApp.class)
public class AiAppVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应用ID
     */
    private Long appId;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 描述
     */
    private String description;

    /**
     * 图标
     */
    private String icon;

    /**
     * 应用类型（simple/workflow）
     */
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
    private Long modelId;

    /**
     * 模型名称（列表展示用，服务层组装）
     */
    private String modelName;

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
     * 公开访问开关（0否 1是）
     */
    private String shareEnabled;

    /**
     * 公开访问码（仅管理端返回）
     */
    private String shareCode;

    /**
     * IP 白名单
     */
    private String ipWhitelist;

    /**
     * 状态（0启用 1停用）
     */
    private String status;

    /**
     * 关联知识库ID（组装用）
     */
    private List<Long> kbIds;

    /**
     * 挂载工具ID（组装用）
     */
    private List<Long> toolIds;

    /**
     * 创建时间
     */
    private Date createTime;

}
