package org.zhiku.ai.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zhiku.ai.app.domain.AiConversation;
import org.zhiku.ai.app.domain.AiMessage;
import org.zhiku.ai.app.domain.vo.AiConversationVo;
import org.zhiku.ai.app.domain.vo.AiMessageVo;
import org.zhiku.ai.app.domain.vo.CitationVo;
import org.zhiku.ai.app.domain.vo.ToolCallRecordVo;
import org.zhiku.ai.app.mapper.AiConversationMapper;
import org.zhiku.ai.app.mapper.AiMessageMapper;
import org.zhiku.ai.app.runner.ChatTurn;
import org.zhiku.ai.app.service.IAiConversationService;
import org.zhiku.ai.knowledge.domain.KbChunk;
import org.zhiku.ai.knowledge.mapper.KbChunkMapper;
import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.json.utils.JsonUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话与消息 Service 实现（KnowledgeQaHandler 的落库 Port）
 *
 * @author zhiku
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AiConversationServiceImpl implements IAiConversationService {

    private final AiConversationMapper baseMapper;

    private final AiMessageMapper messageMapper;

    private final KbChunkMapper chunkMapper;

    @Override
    public Long ensureConversation(Long appId, Long conversationId, String firstQuestion, String source, Long userId) {
        if (conversationId != null) {
            AiConversation existing = baseMapper.selectById(conversationId);
            if (existing == null || !existing.getAppId().equals(appId)) {
                throw new ServiceException("会话不存在或不属于该应用");
            }
            return conversationId;
        }
        AiConversation conversation = new AiConversation();
        conversation.setConversationId(IdWorker.getId());
        conversation.setAppId(appId);
        conversation.setTitle(firstQuestion != null && firstQuestion.length() > 20
            ? firstQuestion.substring(0, 20) : firstQuestion);
        conversation.setSource(source == null ? "0" : source);
        conversation.setCreateBy(userId);
        conversation.setCreateTime(new java.util.Date());
        baseMapper.insert(conversation);
        return conversation.getConversationId();
    }

    @Override
    public Long appendUserMessage(Long conversationId, String content, Long userId) {
        return append(conversationId, "1", content, null, null, null, null, userId);
    }

    @Override
    public Long appendAssistantMessage(Long conversationId, String content, List<CitationVo> citations,
                                       String toolCallsJson, Integer promptTokens, Integer completionTokens, Long userId) {
        return append(conversationId, "2", content,
            citations == null || citations.isEmpty() ? null : JsonUtils.toJsonString(citations),
            toolCallsJson, promptTokens, completionTokens, userId);
    }

    private Long append(Long conversationId, String role, String content, String citationJson,
                        String toolCallsJson, Integer promptTokens, Integer completionTokens, Long userId) {
        // seq = max(seq)+1（单会话写入串行于虚拟线程内，无并发竞争）
        Integer currentMax = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId))
            .stream().map(AiMessage::getSeq).filter(s -> s != null).max(Integer::compareTo).orElse(0);
        AiMessage message = new AiMessage();
        message.setMessageId(IdWorker.getId());
        message.setConversationId(conversationId);
        message.setSeq(currentMax + 1);
        message.setRole(role);
        message.setContent(content);
        message.setCitation(citationJson);
        message.setToolCalls(toolCallsJson);
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setCreateBy(userId);
        message.setCreateTime(new java.util.Date());
        messageMapper.insert(message);
        return message.getMessageId();
    }

    @Override
    public List<ChatTurn> recentTurns(Long conversationId, int maxTurns) {
        List<AiMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
            .eq(AiMessage::getConversationId, conversationId)
            .orderByAsc(AiMessage::getSeq));
        // 组装 user/assistant 成对轮
        List<ChatTurn> turns = new ArrayList<>();
        String pendingQuestion = null;
        for (AiMessage message : messages) {
            if ("1".equals(message.getRole())) {
                pendingQuestion = message.getContent();
            } else if ("2".equals(message.getRole()) && pendingQuestion != null) {
                turns.add(new ChatTurn(pendingQuestion, message.getContent() == null ? "" : message.getContent()));
                pendingQuestion = null;
            }
        }
        if (turns.size() > maxTurns) {
            return turns.subList(turns.size() - maxTurns, turns.size());
        }
        return turns;
    }

    @Override
    public List<AiConversationVo> listByApp(Long appId, String source) {
        LambdaQueryWrapper<AiConversation> lqw = new LambdaQueryWrapper<AiConversation>()
            .eq(AiConversation::getAppId, appId)
            .eq(source != null, AiConversation::getSource, source)
            .orderByDesc(AiConversation::getConversationId);
        if ("0".equals(source)) {
            lqw.eq(AiConversation::getCreateBy, org.zhiku.common.satoken.utils.LoginHelper.getUserId());
        }
        return baseMapper.selectVoList(lqw);
    }

    @Override
    public Boolean rename(Long conversationId, String title) {
        AiConversation conversation = requireConversation(conversationId);
        conversation.setTitle(title);
        return baseMapper.updateById(conversation) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteById(Long conversationId) {
        requireConversation(conversationId);
        messageMapper.delete(new LambdaQueryWrapper<AiMessage>().eq(AiMessage::getConversationId, conversationId));
        return baseMapper.deleteById(conversationId) > 0;
    }

    @Override
    public List<AiMessageVo> listMessages(Long conversationId) {
        List<AiMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
            .eq(AiMessage::getConversationId, conversationId)
            .orderByAsc(AiMessage::getSeq));
        List<AiMessageVo> vos = new ArrayList<>();
        for (AiMessage message : messages) {
            AiMessageVo vo = new AiMessageVo();
            vo.setMessageId(message.getMessageId());
            vo.setConversationId(message.getConversationId());
            vo.setSeq(message.getSeq());
            vo.setRole(message.getRole());
            vo.setContent(message.getContent());
            vo.setPromptTokens(message.getPromptTokens());
            vo.setCompletionTokens(message.getCompletionTokens());
            vo.setCreateTime(message.getCreateTime());
            if (message.getCitation() != null) {
                vo.setCitations(JsonUtils.parseArray(message.getCitation(), CitationVo.class));
            }
            if (message.getToolCalls() != null) {
                vo.setToolCalls(JsonUtils.parseArray(message.getToolCalls(), ToolCallRecordVo.class));
            }
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public void assertOwnership(Long conversationId, Long appId, String source) {
        AiConversation conversation = requireConversation(conversationId);
        if (!conversation.getAppId().equals(appId)) {
            throw new ServiceException("会话不属于该应用");
        }
        if (source != null && !source.equals(conversation.getSource())) {
            throw new ServiceException("会话来源不符");
        }
    }

    @Override
    public ChunkContext chunkContext(Long chunkId) {
        KbChunk chunk = chunkMapper.selectById(chunkId);
        if (chunk == null) {
            throw new ServiceException("分块不存在");
        }
        String prev = pickNeighbor(chunk, -1);
        String next = pickNeighbor(chunk, 1);
        return new ChunkContext(null, chunk.getHeadingPath(), chunk.getSeq(), prev, chunk.getContent(), next);
    }

    private String pickNeighbor(KbChunk chunk, int offset) {
        return chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getDocumentId, chunk.getDocumentId())
                .eq(KbChunk::getSeq, chunk.getSeq() + offset))
            .stream().findFirst().map(KbChunk::getContent).orElse(null);
    }

    private AiConversation requireConversation(Long conversationId) {
        AiConversation conversation = baseMapper.selectById(conversationId);
        if (conversation == null) {
            throw new ServiceException("会话不存在：" + conversationId);
        }
        return conversation;
    }
}
