package org.zhiku.ai.app.service;

import org.zhiku.ai.app.domain.vo.AiConversationVo;
import org.zhiku.ai.app.domain.vo.AiMessageVo;
import org.zhiku.ai.app.domain.vo.CitationVo;
import org.zhiku.ai.app.runner.ChatTurn;
import org.zhiku.common.core.domain.PageResult;
import org.zhiku.common.mybatis.core.page.PageQuery;

import java.util.List;

/**
 * 会话与消息 Service 接口（KnowledgeQaHandler 的落库 Port；Task 6 完整实现）
 *
 * @author zhiku
 */
public interface IAiConversationService {

    /**
     * 会话不存在时新建（title=首问前20字），返回会话ID
     */
    Long ensureConversation(Long appId, Long conversationId, String firstQuestion, String source, Long userId);

    /**
     * 追加用户消息，返回消息ID
     */
    Long appendUserMessage(Long conversationId, String content, Long userId);

    /**
     * 追加助手消息（含 citation/toolCalls/tokens），返回消息ID
     */
    Long appendAssistantMessage(Long conversationId, String content, List<CitationVo> citations,
                                String toolCallsJson, Integer promptTokens, Integer completionTokens, Long userId);

    /**
     * 读会话最近 maxTurns 轮历史（升序）
     */
    List<ChatTurn> recentTurns(Long conversationId, int maxTurns);

    /**
     * 会话列表（应用维度；source='0' 内部按用户隔离，'1' 公开全量）
     */
    List<AiConversationVo> listByApp(Long appId, String source);

    /**
     * 重命名
     */
    Boolean rename(Long conversationId, String title);

    /**
     * 删除（级联删消息）
     */
    Boolean deleteById(Long conversationId);

    /**
     * 消息历史（seq 升序全量，citation/toolCalls 反序列化）
     */
    List<AiMessageVo> listMessages(Long conversationId);

    /**
     * 校验会话归属（应用一致 + 来源一致），不符抛 ServiceException
     */
    void assertOwnership(Long conversationId, Long appId, String source);

    /**
     * 分块上下文（seq±1 相邻三块内容）
     */
    ChunkContext chunkContext(Long chunkId);

    /**
     * 分块上下文（当前块 + 上下相邻块，供 citation 弹窗展示）
     */
    record ChunkContext(String documentName, String headingPath, Integer seq,
                        String prevContent, String content, String nextContent) {
    }
}
