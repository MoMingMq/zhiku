package org.zhiku.ai.app.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.zhiku.ai.app.domain.vo.AiAppVo;
import org.zhiku.ai.app.runner.AppChatCommand;
import org.zhiku.ai.app.runner.ChatTypeRegistry;
import org.zhiku.ai.app.service.IAiAppService;
import org.zhiku.ai.app.service.IAiConversationService;
import org.zhiku.common.core.domain.R;
import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.redis.annotation.RateLimiter;
import org.zhiku.common.web.core.BaseController;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 公开应用对话端点（免登录；security.excludes 放行，服务端自行校验 share_code 与 IP 白名单）
 *
 * @author zhiku
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/public/app")
public class AiPublicAppController extends BaseController {

    private final IAiAppService appService;

    private final ChatTypeRegistry chatTypeRegistry;

    private final IAiConversationService conversationService;

    /**
     * 公开应用信息（名称/描述/开场白/推荐问题；不含配置）
     */
    @GetMapping("/{shareCode}")
    public R<Map<String, Object>> info(@NotBlank @PathVariable String shareCode) {
        AiAppVo app = requirePublicApp(shareCode, null);
        return R.ok(Map.of(
            "appName", app.getAppName(),
            "description", app.getDescription() == null ? "" : app.getDescription(),
            "prologue", app.getPrologue() == null ? "" : app.getPrologue(),
            "suggestedQuestions", app.getSuggestedQuestions() == null ? "[]" : app.getSuggestedQuestions()));
    }

    /**
     * 公开对话（SSE；按 IP 限流）
     */
    @RateLimiter(key = "publicAppChat", time = 60, count = 20)
    @PostMapping(value = "/{shareCode}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@NotBlank @PathVariable String shareCode, @RequestBody AiChatController.ChatBody body,
                           HttpServletRequest request, HttpServletResponse response) {
        AiAppVo app = requirePublicApp(shareCode, request);
        String chatType = app.getChatType() == null ? "knowledge_qa" : app.getChatType();
        return chatTypeRegistry.get(chatType).chat(new AppChatCommand(
            app.getAppId(), body.conversationId(), body.message(), "1", null, response));
    }

    /**
     * 公开会话消息历史（校验归属：会话属于该应用且来源为公开）
     */
    @GetMapping("/{shareCode}/conversation/{conversationId}/messages")
    public R<List<org.zhiku.ai.app.domain.vo.AiMessageVo>> messages(
        @NotBlank @PathVariable String shareCode, @NotNull @PathVariable Long conversationId) {
        AiAppVo app = requirePublicApp(shareCode, null);
        conversationService.assertOwnership(conversationId, app.getAppId(), "1");
        return R.ok(conversationService.listMessages(conversationId));
    }

    /**
     * 校验 share_code 有效 + IP 白名单；失败统一 404/403 语义
     */
    private AiAppVo requirePublicApp(String shareCode, HttpServletRequest request) {
        AiAppVo app = appService.findByShareCode(shareCode);
        if (app == null) {
            throw new ServiceException("链接不存在或已失效");
        }
        if (request != null && !IpWhitelistMatcher.isAllowed(request, app.getIpWhitelist())) {
            throw new ServiceException("当前 IP 无访问权限");
        }
        return app;
    }

}
