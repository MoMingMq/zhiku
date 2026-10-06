package org.zhiku.ai.app.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.zhiku.ai.app.domain.vo.AiConversationVo;
import org.zhiku.ai.app.domain.vo.AiMessageVo;
import org.zhiku.ai.app.runner.AppChatCommand;
import org.zhiku.ai.app.runner.ChatTypeRegistry;
import org.zhiku.ai.app.service.IAiConversationService;
import org.zhiku.common.core.domain.R;
import org.zhiku.common.log.annotation.Log;
import org.zhiku.common.log.enums.BusinessType;
import org.zhiku.common.redis.annotation.RateLimiter;
import org.zhiku.common.satoken.utils.LoginHelper;
import org.zhiku.common.web.core.BaseController;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 应用对话（SSE 流式）与会话管理
 *
 * @author zhiku
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/app")
public class AiChatController extends BaseController {

    private final ChatTypeRegistry chatTypeRegistry;

    private final org.zhiku.ai.app.service.IAiAppService appService;

    private final IAiConversationService conversationService;

    /**
     * 对话（SSE 流式；帧协议：citation/delta/tool_call/tool_result/done/error）
     */
    @SaCheckPermission("ai:app:chat")
    @Log(title = "应用对话", businessType = BusinessType.OTHER)
    @RateLimiter(key = "appChat", time = 60, count = 20)
    @PostMapping(value = "/{appId}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@NotNull @PathVariable Long appId, @RequestBody ChatBody body,
                           HttpServletResponse response) {
        // 按应用配置的对话类型路由（app_type=simple 唯一形态）
        var appVo = appService.queryById(appId);
        if (appVo == null) {
            throw new org.zhiku.common.core.exception.ServiceException("应用不存在：" + appId);
        }
        String chatType = appVo.getChatType() == null ? "knowledge_qa" : appVo.getChatType();
        return chatTypeRegistry.get(chatType).chat(new AppChatCommand(
            appId, body.conversationId(), body.message(), "0", LoginHelper.getUserId(), response));
    }

    /**
     * 会话列表（当前用户，应用维度）
     */
    @SaCheckPermission("ai:app:chat")
    @GetMapping("/{appId}/conversation/list")
    public R<List<AiConversationVo>> conversations(@NotNull @PathVariable Long appId) {
        return R.ok(conversationService.listByApp(appId, "0"));
    }

    /**
     * 重命名会话
     */
    @SaCheckPermission("ai:app:chat")
    @Log(title = "应用对话", businessType = BusinessType.UPDATE)
    @PutMapping("/conversation/{conversationId}/rename")
    public R<Void> rename(@NotNull @PathVariable Long conversationId, @RequestBody RenameBody body) {
        return toAjax(conversationService.rename(conversationId, body.title()));
    }

    /**
     * 删除会话（级联消息）
     */
    @SaCheckPermission("ai:app:chat")
    @Log(title = "应用对话", businessType = BusinessType.DELETE)
    @DeleteMapping("/conversation/{conversationId}")
    public R<Void> removeConversation(@NotNull @PathVariable Long conversationId) {
        return toAjax(conversationService.deleteById(conversationId));
    }

    /**
     * 消息历史（含 citation/toolCalls 反序列化，完整回放）
     */
    @SaCheckPermission("ai:app:chat")
    @GetMapping("/conversation/{conversationId}/messages")
    public R<List<AiMessageVo>> messages(@NotNull @PathVariable Long conversationId) {
        return R.ok(conversationService.listMessages(conversationId));
    }

    /**
     * 分块上下文（citation 点击弹窗：seq±1 相邻块）
     */
    @SaCheckPermission("ai:app:chat")
    @GetMapping("/chunk/{chunkId}/context")
    public R<IAiConversationService.ChunkContext> chunkContext(@NotNull @PathVariable Long chunkId) {
        return R.ok(conversationService.chunkContext(chunkId));
    }

    /**
     * 对话请求体
     */
    public record ChatBody(Long conversationId, String message) {
    }

    /**
     * 重命名请求体
     */
    public record RenameBody(String title) {
    }

}
