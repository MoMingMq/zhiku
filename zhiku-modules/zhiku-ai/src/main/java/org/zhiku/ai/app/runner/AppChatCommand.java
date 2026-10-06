package org.zhiku.ai.app.runner;

import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 对话执行命令（管理端与公开端点共用；handler 据此执行全链路）
 *
 * @author zhiku
 */
@Data
@AllArgsConstructor
public class AppChatCommand {

    /**
     * 应用ID
     */
    private Long appId;

    /**
     * 会话ID（空则新建会话）
     */
    private Long conversationId;

    /**
     * 用户问题
     */
    private String question;

    /**
     * 来源（0内部 1公开匿名）
     */
    private String source;

    /**
     * 发起人（公开匿名为空，审计与归属用）
     */
    private Long userId;

    /**
     * 响应对象（写 SSE 响应头用）
     */
    private HttpServletResponse response;

}
