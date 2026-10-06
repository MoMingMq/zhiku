package org.zhiku.ai.app.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 对话会话对象 ai_conversation
 *
 * @author zhiku
 */
@Data
@TableName("ai_conversation")
public class AiConversation {

    /**
     * 会话ID
     */
    @TableId(value = "conversation_id")
    private Long conversationId;

    /**
     * 应用ID
     */
    private Long appId;

    /**
     * 会话标题（默认首问前20字）
     */
    private String title;

    /**
     * 来源（0内部 1公开匿名，公开会话 create_by 空）
     */
    private String source;

    /**
     * 创建者（公开会话为空）
     */
    private Long createBy;

    /**
     * 创建时间
     */
    private Date createTime;

}
