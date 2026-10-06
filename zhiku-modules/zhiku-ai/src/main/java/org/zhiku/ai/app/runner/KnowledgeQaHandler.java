package org.zhiku.ai.app.runner;

import cn.hutool.core.convert.Convert;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zhiku.ai.app.domain.AiApp;
import org.zhiku.ai.app.domain.vo.AppConfigField;
import org.zhiku.ai.app.domain.vo.CitationVo;
import org.zhiku.ai.app.domain.vo.ToolCallRecordVo;
import org.zhiku.ai.app.mapper.AiAppMapper;
import org.zhiku.ai.app.service.IAiConversationService;
import org.zhiku.ai.knowledge.domain.vo.RetrievalHit;
import org.zhiku.ai.knowledge.domain.vo.RetrievalQuery;
import org.zhiku.ai.knowledge.service.IAiRetrievalService;
import org.zhiku.ai.model.client.AiModelClientFactory;
import org.zhiku.ai.model.domain.AiModel;
import org.zhiku.ai.model.mapper.AiModelMapper;
import org.zhiku.ai.tool.execute.AiToolCallbackFactory;
import org.zhiku.ai.tool.service.IAiToolService;
import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.json.utils.JsonUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 知识库问答对话类型（M4 唯一实现）：落库→检索→组装→流式生成→语义帧→落库
 * <p>工具熔断：SseEmitter 120s 超时硬上限；客户端 abort 触发 onError → dispose 并落库半截内容。
 *
 * @author zhiku
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeQaHandler implements ChatTypeHandler {

    /**
     * 类型码
     */
    public static final String TYPE = "knowledge_qa";

    /**
     * 流式执行线程池（JDK21 虚拟线程，IO 密集）
     */
    private static final ExecutorService STREAM_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 滑窗保留的最大历史轮数（超出按 token 预算从最旧裁剪）
     */
    private static final int MAX_HISTORY_TURNS = 20;

    /**
     * 模型未配置上下文时的防御性预算
     */
    private static final int DEFAULT_TOKEN_BUDGET = 24_000;

    /**
     * 上下文预算 = 模型上下文窗口 - 预留输出（模型 maxOutputTokens 或默认 4096）- 提示词余量
     */
    private int resolveTokenBudget(AiModel model) {
        if (model.getContextWindow() == null || model.getContextWindow() <= 0) {
            return DEFAULT_TOKEN_BUDGET;
        }
        int reserved = model.getMaxOutputTokens() != null && model.getMaxOutputTokens() > 0
            ? model.getMaxOutputTokens() : 4096;
        return Math.max(1024, model.getContextWindow() - reserved - 512);
    }

    /**
     * 默认系统提示词（应用未配置时兜底；防编造是产品底线）
     */
    public static final String DEFAULT_SYSTEM_PROMPT =
        "你是企业知识库助手。仅根据提供的【参考资料】回答用户问题；资料中没有答案时明确说明无法回答，不要编造。";

    private final AiAppMapper appMapper;

    private final AiModelMapper modelMapper;

    private final AiModelClientFactory clientFactory;

    private final IAiRetrievalService retrievalService;

    private final IAiToolService toolService;

    private final IAiConversationService conversationService;

    private final PromptAssembler assembler;

    private final ChatStreamFrameExtractor extractor = new ChatStreamFrameExtractor();

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String typeName() {
        return "知识库问答";
    }

    @Override
    public List<AppConfigField> configFields() {
        return List.of(
            AppConfigField.multi("kbIds", "知识库与检索设置", "kb-config", true, "关联知识库并配置检索参数（重排由各知识库自身配置驱动）"),
            AppConfigField.number("topK", "检索条数", 5, 1, 20, "多库召回并集后的 Top N"),
            AppConfigField.number("minScore", "相似度阈值", 5, 0, 100, "低于该分数的命中被过滤（百分比，0=不过滤）"));
    }

    @Override
    public String defaultConfig() {
        return "{\"kbIds\":[],\"topK\":5,\"minScore\":50}";
    }

    @Override
    public void validateConfig(String configJson) {
        QaConfig config = parseConfig(configJson);
        if (config.topK() < 1 || config.topK() > 20) {
            throw new ServiceException("检索条数必须在 1-20 之间");
        }
    }

    @Override
    public SseEmitter chat(AppChatCommand command) {
        if (command.getResponse() != null) {
            command.getResponse().setHeader("X-Accel-Buffering", "no");
            command.getResponse().setHeader("Cache-Control", "no-cache");
        }
        SseEmitter emitter = new SseEmitter(120_000L);
        STREAM_EXECUTOR.execute(() -> doChat(command, emitter));
        return emitter;
    }

    private void doChat(AppChatCommand command, SseEmitter emitter) {
        ChatFrameWriter writer = new ChatFrameWriter(emitter);
        Long conversationId = null;
        Long assistantMessageId = IdWorker.getId();
        StringBuilder content = new StringBuilder();
        List<ToolCallRecordVo> toolCalls = new ArrayList<>();
        List<CitationVo> citations = List.of();
        Integer[] tokens = new Integer[2];
        AtomicReference<Disposable> disposable = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        try {
            AiApp app = appMapper.selectById(command.getAppId());
            if (app == null) {
                throw new ServiceException("应用不存在：" + command.getAppId());
            }
            AiModel model = modelMapper.selectById(app.getModelId());
            if (model == null) {
                throw new ServiceException("对话模型不存在，请先在设置页选择模型");
            }
            QaConfig config = parseConfig(app.getConfig());
            // 上下文策略（业界共识：滑窗 + 按模型上下文预算截断；摘要压缩下期增强）
            int tokenBudget = resolveTokenBudget(model);
            int maxTurns = MAX_HISTORY_TURNS;

            // ① 会话与用户消息先落库；assistant 消息ID 预生成（工具审计挂此ID，M3 契约零改动）
            conversationId = conversationService.ensureConversation(
                command.getAppId(), command.getConversationId(), command.getQuestion(), command.getSource(), command.getUserId());
            conversationService.appendUserMessage(conversationId, command.getQuestion(), command.getUserId());

            // ② 检索（M2 唯一入口；失败明确报错不静默）→ citation 帧（首 token 前下发）
            List<RetrievalHit> hits = config.kbIds().isEmpty() ? List.of()
                : retrievalService.search(buildQuery(config, command.getQuestion()));
            citations = hits.stream().map(this::toCitation).toList();
            writer.citation(citations);

            // ③ 组装（预算截断纯函数）+ 消息序列
            List<ChatTurn> history = conversationService.recentTurns(conversationId, maxTurns);
            String systemPrompt = app.getSystemPrompt() == null || app.getSystemPrompt().isBlank()
                ? DEFAULT_SYSTEM_PROMPT : app.getSystemPrompt();
            PromptAssembler.AssembleResult assembled = assembler.assemble(
                systemPrompt, hits, history, command.getQuestion(), maxTurns, tokenBudget);
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt + "\n\n" + assembled.contextBlock()));
            assembled.turns().forEach(t -> {
                messages.add(new UserMessage(t.question()));
                messages.add(new AssistantMessage(t.answer()));
            });
            messages.add(new UserMessage(command.getQuestion()));

            // ④ runtime options：模型标识+参数覆盖+工具回调+ToolContext 审计回填（一次组装，merge 语义）
            List<ToolCallback> callbacks = ToolCallStreamDecorator.decorateAll(
                toolService.listCallbacksByApp(command.getAppId()), writer, toolCalls);
            Map<String, Object> toolContext = new HashMap<>();
            toolContext.put(AiToolCallbackFactory.CTX_APP_ID, command.getAppId());
            toolContext.put(AiToolCallbackFactory.CTX_MESSAGE_ID, assistantMessageId);
            OpenAiChatOptions runtimeOptions = buildRuntimeOptions(model, app.getModelParams(), callbacks, toolContext);
            Prompt prompt = new Prompt(messages, runtimeOptions);

            // ⑤ 流式订阅（工具循环由 Spring AI 内部执行）；完成/异常释放闭锁
            ChatModel chatModel = clientFactory.getChatModel(app.getModelId());
            Disposable d = chatModel.stream(prompt)
                .doOnNext(resp -> {
                    Integer[] usage = extractor.handle(resp, content, writer::delta);
                    if (usage != null) {
                        tokens[0] = usage[0];
                        tokens[1] = usage[1];
                    }
                })
                .doFinally(signal -> latch.countDown())
                .subscribe();
            disposable.set(d);
            final Long convId = conversationId;
            emitter.onCompletion(() -> safeDispose(disposable));
            emitter.onError(t -> {
                safeDispose(disposable);
                log.info("对话流中断（客户端 abort）conversationId={}", convId);
            });
            emitter.onTimeout(() -> {
                safeDispose(disposable);
                writer.error("生成超时，请缩短问题或稍后重试", true);
            });
            // ⑥ 虚拟线程阻塞等待流结束（超时由 SseEmitter 120s 兜底）
            latch.await();
            // ⑦ 落库 + 终帧
            conversationService.appendAssistantMessage(conversationId, content.toString(), citations,
                toolCalls.isEmpty() ? null : JsonUtils.toJsonString(toolCalls), tokens[0], tokens[1], command.getUserId());
            writer.done(assistantMessageId, conversationId, tokens[0], tokens[1]);
            emitter.complete();
        } catch (Exception e) {
            log.warn("应用对话失败 appId={}", command.getAppId(), e);
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            writer.error("对话失败：" + message, true);
            savePartial(conversationId, command, content, citations, toolCalls);
            emitter.complete();
        }
    }

    /**
     * 中断/异常时保存半截内容（连接已断不发帧）
     */
    private void savePartial(Long conversationId, AppChatCommand command, StringBuilder content,
                             List<CitationVo> citations, List<ToolCallRecordVo> toolCalls) {
        if (conversationId == null || content.length() == 0) {
            return;
        }
        try {
            conversationService.appendAssistantMessage(conversationId, content.toString(), citations,
                toolCalls.isEmpty() ? null : JsonUtils.toJsonString(toolCalls), null, null, command.getUserId());
        } catch (Exception ex) {
            log.warn("中断内容落库失败", ex);
        }
    }

    private CitationVo toCitation(RetrievalHit hit) {
        CitationVo vo = new CitationVo();
        vo.setChunkId(hit.getChunkId());
        vo.setDocumentId(hit.getDocumentId());
        vo.setDocumentName(hit.getDocumentName());
        vo.setSeq(hit.getSeq());
        vo.setHeadingPath(hit.getHeadingPath());
        vo.setScore(hit.getScore());
        String text = hit.getContent() == null ? "" : hit.getContent();
        vo.setSnippet(text.length() > 300 ? text.substring(0, 300) : text);
        return vo;
    }

    private QaConfig parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return new QaConfig(List.of(), 5, 0.5f);
        }
        Map<String, Object> map = JsonUtils.parseMap(configJson);
        // 兼容旧 config（rerankEnabled/maxTurns 键忽略）；minScore 前端按 0-100 百分比存储
        float minScore = Convert.toFloat(map.get("minScore"), 50f);
        if (minScore > 1f) {
            minScore = minScore / 100f;
        }
        return new QaConfig(
            Convert.toList(Long.class, map.getOrDefault("kbIds", List.of())),
            Convert.toInt(map.get("topK"), 5),
            minScore);
    }

    private RetrievalQuery buildQuery(QaConfig config, String question) {
        RetrievalQuery query = new RetrievalQuery();
        query.setKbIds(config.kbIds());
        query.setText(question);
        query.setTopK(config.topK());
        query.setMinScore(config.minScore());
        // 重排是否实际发生由各知识库配置的 rerank_model_id 决定（未配置库回落系统默认，均无则跳过）
        query.setRerankEnabled(true);
        return query;
    }

    /**
     * runtime options：模型标识 + 应用参数覆盖 + 工具回调 + ToolContext（Spring AI 运行时 merge 进工厂默认 options）
     */
    private OpenAiChatOptions buildRuntimeOptions(AiModel model, String modelParamsJson,
                                                  List<ToolCallback> callbacks, Map<String, Object> toolContext) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
            .model(model.getModelCode())
            .toolCallbacks(callbacks)
            .toolContext(toolContext);
        if (modelParamsJson != null && !modelParamsJson.isBlank()) {
            Map<String, Object> params = JsonUtils.parseMap(modelParamsJson);
            if (params.get("temperature") instanceof Number n) {
                builder.temperature(n.doubleValue());
            }
            if (params.get("maxTokens") instanceof Number n) {
                builder.maxTokens(n.intValue());
            }
            if (params.get("topP") instanceof Number n) {
                builder.topP(n.doubleValue());
            }
        } else if (model.getMaxOutputTokens() != null && model.getMaxOutputTokens() > 0) {
            // 应用未覆盖参数时回落模型级最大输出配置
            builder.maxTokens(model.getMaxOutputTokens());
        }
        return builder.build();
    }

    private void safeDispose(AtomicReference<Disposable> disposable) {
        Disposable d = disposable.get();
        if (d != null && !d.isDisposed()) {
            d.dispose();
        }
    }

    /**
     * knowledge_qa 专属配置
     */
    record QaConfig(List<Long> kbIds, int topK, float minScore) {
    }
}
