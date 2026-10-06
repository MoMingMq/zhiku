package org.zhiku.ai.app.service.impl;

import cn.hutool.core.convert.Convert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zhiku.ai.app.domain.AiApp;
import org.zhiku.ai.app.domain.AiAppKbRel;
import org.zhiku.ai.app.domain.AiConversation;
import org.zhiku.ai.app.domain.AiMessage;
import org.zhiku.ai.app.domain.bo.AiAppBo;
import org.zhiku.ai.app.domain.vo.AiAppStatsVo;
import org.zhiku.ai.app.domain.vo.AiAppVo;
import org.zhiku.ai.app.domain.vo.ChatTypeVo;
import org.zhiku.ai.app.mapper.AiAppKbRelMapper;
import org.zhiku.ai.app.mapper.AiAppMapper;
import org.zhiku.ai.app.mapper.AiConversationMapper;
import org.zhiku.ai.app.mapper.AiMessageMapper;
import org.zhiku.ai.app.runner.ChatTypeRegistry;
import org.zhiku.ai.app.service.IAiAppService;
import org.zhiku.ai.app.service.IAiConversationService;
import org.zhiku.ai.knowledge.mapper.KbDatasetMapper;
import org.zhiku.ai.model.domain.AiModel;
import org.zhiku.ai.model.mapper.AiModelMapper;
import org.zhiku.ai.tool.mapper.AiAppToolRelMapper;
import org.zhiku.ai.tool.domain.AiAppToolRel;
import org.zhiku.common.core.domain.PageResult;
import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.core.utils.MapstructUtils;
import org.zhiku.common.core.utils.StringUtils;
import org.zhiku.common.json.utils.JsonUtils;
import org.zhiku.common.mybatis.core.page.PageQuery;
import org.zhiku.common.satoken.utils.LoginHelper;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 应用管理 Service 实现
 *
 * @author zhiku
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AiAppServiceImpl implements IAiAppService {

    /**
     * 应用类型：本期仅简易应用
     */
    private static final String APP_TYPE_SIMPLE = "simple";

    /**
     * IP 白名单格式：单 IP 或 CIDR，逗号分隔
     */
    private static final Pattern IP_OR_CIDR =
        Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}(/\\d{1,2})?$");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AiAppMapper baseMapper;

    private final AiAppKbRelMapper kbRelMapper;

    private final AiAppToolRelMapper toolRelMapper;

    private final AiConversationMapper conversationMapper;

    private final AiMessageMapper messageMapper;

    private final AiModelMapper modelMapper;

    private final KbDatasetMapper datasetMapper;

    private final ChatTypeRegistry chatTypeRegistry;

    private final IAiConversationService conversationService;

    @Override
    public PageResult<AiAppVo> queryPageList(AiAppBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<AiApp> lqw = new LambdaQueryWrapper<AiApp>()
            .like(StringUtils.isNotBlank(bo.getAppName()), AiApp::getAppName, bo.getAppName())
            .eq(StringUtils.isNotBlank(bo.getStatus()), AiApp::getStatus, bo.getStatus())
            .orderByDesc(AiApp::getAppId);
        Page<AiAppVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        result.getRecords().forEach(this::assemble);
        return PageResult.build(result.getRecords(), result.getTotal());
    }

    @Override
    public AiAppVo queryById(Long appId) {
        AiAppVo vo = baseMapper.selectVoById(appId);
        if (vo != null) {
            assemble(vo);
        }
        return vo;
    }

    @Override
    public Boolean insertByBo(AiAppBo bo) {
        if (bo.getModelId() == null) {
            bo.setModelId(requireDefaultLlmModel());
        }
        validate(bo, null);
        AiApp add = toEntity(bo);
        if (StringUtils.isBlank(add.getChatType())) {
            add.setChatType("knowledge_qa");
        }
        if (StringUtils.isBlank(add.getSystemPrompt())) {
            add.setSystemPrompt(org.zhiku.ai.app.runner.KnowledgeQaHandler.DEFAULT_SYSTEM_PROMPT);
        }
        boolean ok = baseMapper.insert(add) > 0;
        if (ok) {
            bo.setAppId(add.getAppId());
            syncKbRel(add.getAppId(), kbIdsOf(bo.getConfig()));
        }
        return ok;
    }

    @Override
    public Boolean updateByBo(AiAppBo bo) {
        validate(bo, bo.getAppId());
        AiApp update = toEntity(bo);
        boolean ok = baseMapper.updateById(update) > 0;
        if (ok && bo.getConfig() != null) {
            syncKbRel(bo.getAppId(), kbIdsOf(bo.getConfig()));
        }
        return ok;
    }

    /**
     * Bo 转 Entity（protected 钩子：单测环境绕开 MapstructUtils 容器依赖）
     */
    protected AiApp toEntity(AiAppBo bo) {
        return MapstructUtils.convert(bo, AiApp.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(List<Long> appIds) {
        if (appIds == null || appIds.isEmpty()) {
            return false;
        }
        // 级联：知识库关系、工具关系、会话与消息
        kbRelMapper.delete(new LambdaQueryWrapper<AiAppKbRel>().in(AiAppKbRel::getAppId, appIds));
        toolRelMapper.delete(new LambdaQueryWrapper<AiAppToolRel>().in(AiAppToolRel::getAppId, appIds));
        List<AiConversation> conversations = conversationMapper.selectList(
            new LambdaQueryWrapper<AiConversation>().in(AiConversation::getAppId, appIds));
        if (!conversations.isEmpty()) {
            List<Long> convIds = conversations.stream().map(AiConversation::getConversationId).toList();
            messageMapper.delete(new LambdaQueryWrapper<AiMessage>().in(AiMessage::getConversationId, convIds));
            conversationMapper.deleteByIds(convIds);
        }
        return baseMapper.deleteByIds(appIds) > 0;
    }

    @Override
    public List<ChatTypeVo> listChatTypes() {
        return chatTypeRegistry.listTypes();
    }

    @Override
    public String toggleShare(Long appId, boolean enabled) {
        AiApp app = requireApp(appId);
        if (enabled) {
            String code = randomCode();
            var luw = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AiApp>();
            luw.eq(AiApp::getAppId, appId).set(AiApp::getShareEnabled, "1").set(AiApp::getShareCode, code);
            baseMapper.update(null, luw);
            return code;
        }
        var luw = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AiApp>();
        luw.eq(AiApp::getAppId, appId).set(AiApp::getShareEnabled, "0");
        baseMapper.update(null, luw);
        return null;
    }

    @Override
    public Boolean updateIpWhitelist(Long appId, String ipWhitelist) {
        requireApp(appId);
        String normalized = ipWhitelist == null ? "" : ipWhitelist.trim();
        if (!normalized.isEmpty()) {
            for (String segment : normalized.split("[,，]")) {
                if (!IP_OR_CIDR.matcher(segment.trim()).matches()) {
                    throw new ServiceException("IP 白名单格式不正确：" + segment + "（支持单 IP 或 CIDR，逗号分隔）");
                }
            }
        }
        var luw = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AiApp>();
        luw.eq(AiApp::getAppId, appId).set(AiApp::getIpWhitelist, normalized);
        return baseMapper.update(null, luw) > 0;
    }

    @Override
    public AiAppStatsVo stats(Long appId) {
        requireApp(appId);
        Map<String, Object> totals = baseMapper.selectAppStatsTotals(appId);
        AiAppStatsVo vo = new AiAppStatsVo();
        vo.setTotalQuestions(Convert.toLong(totals.get("totalQuestions"), 0L));
        vo.setTotalConversations(Convert.toLong(totals.get("totalConversations"), 0L));
        vo.setTotalTokens(Convert.toLong(totals.get("totalTokens"), 0L));
        vo.setTodayQuestions(Convert.toLong(totals.get("todayQuestions"), 0L));
        vo.setLast7d(baseMapper.selectAppStatsDaily(appId).stream().map(row -> {
            AiAppStatsVo.DailyStat stat = new AiAppStatsVo.DailyStat();
            stat.setDate(String.valueOf(row.get("date")));
            stat.setQuestions(Convert.toLong(row.get("questions"), 0L));
            stat.setTokens(Convert.toLong(row.get("tokens"), 0L));
            return stat;
        }).collect(Collectors.toList()));
        return vo;
    }

    @Override
    public AiAppVo findByShareCode(String shareCode) {
        if (StringUtils.isBlank(shareCode)) {
            return null;
        }
        AiAppVo vo = baseMapper.selectVoOne(new LambdaQueryWrapper<AiApp>()
            .eq(AiApp::getShareCode, shareCode)
            .eq(AiApp::getShareEnabled, "1")
            .eq(AiApp::getStatus, "0"));
        return vo;
    }

    /**
     * 新建未选模型时自动落默认对话模型（isDefault 优先，否则最新一个 LLM；一个都没有则引导先配模型）
     */
    private Long requireDefaultLlmModel() {
        AiModel llm = modelMapper.selectOne(new LambdaQueryWrapper<AiModel>()
            .eq(AiModel::getModelType, "1").eq(AiModel::getIsDefault, "1")
            .orderByDesc(AiModel::getModelId).last("limit 1"));
        if (llm == null) {
            llm = modelMapper.selectOne(new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getModelType, "1")
                .orderByDesc(AiModel::getModelId).last("limit 1"));
        }
        if (llm == null) {
            throw new ServiceException("暂无可用对话模型，请先在模型管理配置");
        }
        return llm.getModelId();
    }

    private AiApp requireApp(Long appId) {
        AiApp app = baseMapper.selectById(appId);
        if (app == null) {
            throw new ServiceException("应用不存在：" + appId);
        }
        return app;
    }

    /**
     * 组装关联信息（kbIds/toolIds/modelName）
     */
    private void assemble(AiAppVo vo) {
        vo.setKbIds(kbRelMapper.selectList(new LambdaQueryWrapper<AiAppKbRel>().eq(AiAppKbRel::getAppId, vo.getAppId()))
            .stream().map(AiAppKbRel::getDatasetId).toList());
        vo.setToolIds(toolRelMapper.selectList(new LambdaQueryWrapper<AiAppToolRel>().eq(AiAppToolRel::getAppId, vo.getAppId()))
            .stream().map(AiAppToolRel::getToolId).toList());
        var model = modelMapper.selectById(vo.getModelId());
        if (model != null) {
            vo.setModelName(model.getModelName());
        }
    }

    /**
     * 保存校验：应用类型、对话类型、模型存在性、config
     */
    private void validate(AiAppBo bo, Long excludeId) {
        if (!APP_TYPE_SIMPLE.equals(bo.getAppType())) {
            throw new ServiceException("应用类型暂仅支持简易应用");
        }
        String chatType = StringUtils.isBlank(bo.getChatType()) ? "knowledge_qa" : bo.getChatType();
        chatTypeRegistry.get(chatType).validateConfig(bo.getConfig());
        if (modelMapper.selectById(bo.getModelId()) == null) {
            throw new ServiceException("对话模型不存在：" + bo.getModelId());
        }
        if (StringUtils.isNotBlank(bo.getAppName())) {
            LambdaQueryWrapper<AiApp> dup = new LambdaQueryWrapper<AiApp>().eq(AiApp::getAppName, bo.getAppName());
            if (excludeId != null) {
                dup.ne(AiApp::getAppId, excludeId);
            }
            if (baseMapper.selectCount(dup) > 0) {
                throw new ServiceException("应用名称已存在：" + bo.getAppName());
            }
        }
    }

    /**
     * config.kbIds 与关系表 diff 同步（增删）
     */
    private void syncKbRel(Long appId, List<Long> kbIds) {
        Set<Long> target = new HashSet<>(kbIds == null ? List.of() : kbIds);
        List<AiAppKbRel> existing = kbRelMapper.selectList(
            new LambdaQueryWrapper<AiAppKbRel>().eq(AiAppKbRel::getAppId, appId));
        Set<Long> existingIds = existing.stream().map(AiAppKbRel::getDatasetId).collect(Collectors.toSet());
        // 新增
        List<AiAppKbRel> toInsert = new ArrayList<>();
        Long operator = LoginHelper.getUserId();
        for (Long kbId : target) {
            if (kbId != null && !existingIds.contains(kbId)) {
                AiAppKbRel rel = new AiAppKbRel();
                rel.setRelId(IdWorker.getId());
                rel.setAppId(appId);
                rel.setDatasetId(kbId);
                rel.setCreateBy(operator);
                rel.setCreateTime(new java.util.Date());
                toInsert.add(rel);
            }
        }
        if (!toInsert.isEmpty()) {
            kbRelMapper.insertBatch(toInsert);
        }
        // 删除
        List<Long> toDelete = existing.stream()
            .filter(rel -> !target.contains(rel.getDatasetId()))
            .map(AiAppKbRel::getRelId).toList();
        if (!toDelete.isEmpty()) {
            kbRelMapper.deleteByIds(toDelete);
        }
    }

    private List<Long> kbIdsOf(String configJson) {
        if (StringUtils.isBlank(configJson)) {
            return List.of();
        }
        return Convert.toList(Long.class, JsonUtils.parseMap(configJson).getOrDefault("kbIds", List.of()));
    }

    private String randomCode() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
