package org.zhiku.ai.app.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.zhiku.common.mybatis.core.domain.BaseEntity;

/**
 * AI 应用对象 ai_app
 *
 * @author zhiku
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_app")
public class AiApp extends BaseEntity {

    /**
     * 应用ID
     */
    @TableId(value = "app_id")
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
     * 应用类型（simple简易应用 workflow高级编排-预留）
     */
    private String appType;

    /**
     * 对话类型码（knowledge_qa 等，ChatTypeHandler 路由）
     */
    private String chatType;

    /**
     * 对话类型专属配置 JSON（结构由类型定义）
     */
    private String config;

    /**
     * 对话模型ID（ai_model）
     */
    private Long modelId;

    /**
     * 模型参数覆盖 JSON {name:value}
     */
    private String modelParams;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 开场白（Markdown）
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
     * 公开访问码（开启生成，关闭失效）
     */
    private String shareCode;

    /**
     * IP 白名单（单IP/CIDR 逗号分隔，空=不限）
     */
    private String ipWhitelist;

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
