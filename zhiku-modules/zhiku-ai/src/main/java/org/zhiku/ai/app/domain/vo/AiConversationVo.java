package org.zhiku.ai.app.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.zhiku.ai.app.domain.AiConversation;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 对话会话视图对象 ai_conversation
 *
 * @author zhiku
 */
@Data
@AutoMapper(target = AiConversation.class)
public class AiConversationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 应用ID
     */
    private Long appId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 来源（0内部 1公开）
     */
    private String source;

    /**
     * 创建时间
     */
    private Date createTime;

}
