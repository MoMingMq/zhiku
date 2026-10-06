package org.zhiku.ai.app.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticApplicationContext;
import org.zhiku.ai.app.domain.AiConversation;
import org.zhiku.ai.app.domain.AiMessage;
import org.zhiku.ai.app.domain.bo.AiAppBo;
import org.zhiku.ai.app.mapper.AiAppKbRelMapper;
import org.zhiku.ai.app.mapper.AiAppMapper;
import org.zhiku.ai.app.mapper.AiConversationMapper;
import org.zhiku.ai.app.mapper.AiMessageMapper;
import org.zhiku.ai.app.runner.ChatTypeHandler;
import org.zhiku.ai.app.runner.ChatTypeRegistry;
import org.zhiku.ai.model.mapper.AiModelMapper;
import org.zhiku.ai.knowledge.mapper.KbDatasetMapper;
import org.zhiku.ai.tool.mapper.AiAppToolRelMapper;
import org.zhiku.common.core.exception.ServiceException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AiAppServiceImpl / AiConversationServiceImpl 核心单测
 * <p>JsonUtils 容器引导与 toEntity 钩子对齐 AiModelServiceTest 先例。
 *
 * @author zhiku
 */
@ExtendWith(MockitoExtension.class)
class AiAppServiceTest {

    @Mock
    private AiAppMapper baseMapper;

    @Mock
    private AiAppKbRelMapper kbRelMapper;

    @Mock
    private AiAppToolRelMapper toolRelMapper;

    @Mock
    private AiConversationMapper conversationMapper;

    @Mock
    private AiMessageMapper messageMapper;

    @Mock
    private AiModelMapper modelMapper;

    @Mock
    private KbDatasetMapper datasetMapper;

    @Mock
    private org.zhiku.ai.app.service.IAiConversationService mockConversationService;

    @Mock
    private org.zhiku.ai.knowledge.mapper.KbChunkMapper chunkMapper;

    @Mock
    private ChatTypeHandler qaHandler;

    private AiAppServiceImpl appService;

    private AiConversationServiceImpl conversationService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient().when(qaHandler.type()).thenReturn("knowledge_qa");
        appService = new AiAppServiceImpl(baseMapper, kbRelMapper, toolRelMapper,
            conversationMapper, messageMapper, modelMapper, datasetMapper,
            new ChatTypeRegistry(List.of(qaHandler)), mockConversationService);
        conversationService = new AiConversationServiceImpl(conversationMapper, messageMapper, chunkMapper);
    }

    @BeforeAll
    static void init() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("jsonMapper", tools.jackson.databind.json.JsonMapper.builder().build());
        context.refresh();
        new SpringUtil().setApplicationContext(context);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            org.zhiku.ai.app.domain.AiApp.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            AiConversation.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            AiMessage.class);
    }

    private AiAppBo validBo() {
        AiAppBo bo = new AiAppBo();
        bo.setAppName("客服助手");
        bo.setAppType("simple");
        bo.setChatType("knowledge_qa");
        bo.setConfig("{\"kbIds\":[1,2],\"topK\":5,\"rerankEnabled\":false,\"maxTurns\":10}");
        bo.setModelId(100L);
        return bo;
    }

    @Test
    void nonSimpleAppTypeRejected() {
        AiAppBo bo = validBo();
        bo.setAppType("workflow");
        assertThrows(ServiceException.class, () -> appService.insertByBo(bo));
    }

    @Test
    void unknownChatTypeRejected() {
        AiAppBo bo = validBo();
        bo.setChatType("agent");
        assertThrows(ServiceException.class, () -> appService.insertByBo(bo));
    }

    @Test
    void missingModelRejected() {
        when(modelMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(ServiceException.class, () -> appService.insertByBo(validBo()));
    }

    @Test
    void shareToggleGeneratesFreshCode() {
        org.zhiku.ai.app.domain.AiApp app = new org.zhiku.ai.app.domain.AiApp();
        app.setAppId(1L);
        when(baseMapper.selectById(1L)).thenReturn(app);
        when(baseMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        String code1 = appService.toggleShare(1L, true);
        String code2 = appService.toggleShare(1L, true);
        assertEquals(32, code1.length());
        assertNotEquals(code1, code2, "复开必须换新码使旧链接失效");
        assertEquals(null, appService.toggleShare(1L, false));
    }

    @Test
    void ipWhitelistFormatValidated() {
        org.zhiku.ai.app.domain.AiApp app = new org.zhiku.ai.app.domain.AiApp();
        app.setAppId(1L);
        when(baseMapper.selectById(1L)).thenReturn(app);
        when(baseMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        assertTrue(appService.updateIpWhitelist(1L, "10.0.0.1,192.168.0.0/24"));
        assertThrows(ServiceException.class, () -> appService.updateIpWhitelist(1L, "not-an-ip"));
    }

    @Test
    void recentTurnsPairsUserAssistant() {
        AiMessage u1 = msg("1", "问1");
        AiMessage a1 = msg("2", "答1");
        AiMessage u2 = msg("1", "问2");
        when(messageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(u1, a1, u2));
        List<org.zhiku.ai.app.runner.ChatTurn> turns = conversationService.recentTurns(9L, 10);
        assertEquals(1, turns.size());
        assertEquals("问1", turns.get(0).question());
        assertEquals("答1", turns.get(0).answer());
    }

    private AiMessage msg(String role, String content) {
        AiMessage m = new AiMessage();
        m.setRole(role);
        m.setContent(content);
        return m;
    }
}
