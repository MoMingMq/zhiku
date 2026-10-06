package org.zhiku.ai.tool.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticApplicationContext;
import org.zhiku.ai.tool.domain.AiAppToolRel;
import org.zhiku.ai.tool.domain.AiTool;
import org.zhiku.ai.tool.domain.bo.AiToolBo;
import org.zhiku.ai.tool.execute.AiToolCallbackFactory;
import org.zhiku.ai.tool.execute.ToolExecResult;
import org.zhiku.ai.tool.execute.ToolExecutor;
import org.zhiku.ai.tool.mapper.AiAppToolRelMapper;
import org.zhiku.ai.tool.mapper.AiToolMapper;
import org.zhiku.common.core.exception.ServiceException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiToolServiceImpl 单元测试
 * <p>JsonUtils 静态初始化依赖 Spring 容器：@BeforeAll 向 hutool SpringUtil 注册最小 StaticApplicationContext；
 * MapstructUtils 依赖容器 bean，通过覆写 {@code toEntity} 钩子绕开（对齐 AiModelServiceTest 先例）。
 *
 * @author zhiku
 */
@ExtendWith(MockitoExtension.class)
class AiToolServiceTest {

    @Mock
    private AiToolMapper baseMapper;

    @Mock
    private AiAppToolRelMapper relMapper;

    @Mock
    private ToolExecutor toolExecutor;

    @Mock
    private AiToolCallbackFactory callbackFactory;

    @Spy
    @InjectMocks
    private AiToolServiceImpl service;

    @BeforeAll
    static void initSpringUtil() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("jsonMapper", tools.jackson.databind.json.JsonMapper.builder().build());
        context.refresh();
        new SpringUtil().setApplicationContext(context);
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiTool.class);
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiAppToolRel.class);
    }

    /**
     * 合法最小 Bo
     */
    private AiToolBo validBo() {
        AiToolBo bo = new AiToolBo();
        bo.setToolName("weather_query");
        bo.setDescription("查询城市天气");
        bo.setMethod("GET");
        bo.setUrlTemplate("https://api.example.com/v1/forecast");
        bo.setParamSchema("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}");
        return bo;
    }

    /**
     * 构造 convert stub 用的目标 Entity（手动映射，绕开容器依赖，对齐 AiModelServiceTest 先例）
     */
    private AiTool toEntity(AiToolBo bo) {
        AiTool entity = new AiTool();
        entity.setToolId(bo.getToolId());
        entity.setToolName(bo.getToolName());
        entity.setDescription(bo.getDescription());
        entity.setIcon(bo.getIcon());
        entity.setMethod(bo.getMethod());
        entity.setUrlTemplate(bo.getUrlTemplate());
        entity.setHeaders(bo.getHeaders());
        entity.setQueryParams(bo.getQueryParams());
        entity.setBodyTemplate(bo.getBodyTemplate());
        entity.setParamSchema(bo.getParamSchema());
        entity.setAuthType(bo.getAuthType());
        entity.setAuthKey(bo.getAuthKey());
        entity.setAuthValue(bo.getAuthValue());
        entity.setAllowIntranet(bo.getAllowIntranet());
        entity.setTimeoutSeconds(bo.getTimeoutSeconds());
        entity.setStatus(bo.getStatus());
        return entity;
    }

    @Test
    void duplicateNameRejected() {
        when(baseMapper.selectCount(any())).thenReturn(1L);
        assertThrows(ServiceException.class, () -> service.insertByBo(validBo()));
    }

    @Test
    void illegalToolNameRejected() {
        AiToolBo bo = validBo();
        bo.setToolName("查天气");
        assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        AiToolBo numeric = validBo();
        numeric.setToolName("1weather");
        assertThrows(ServiceException.class, () -> service.insertByBo(numeric));
    }

    @Test
    void nonObjectSchemaRootRejected() {
        AiToolBo bo = validBo();
        bo.setParamSchema("[1,2,3]");
        assertThrows(ServiceException.class, () -> service.insertByBo(bo));
    }

    @Test
    void urlAuthorityPlaceholderRejected() {
        AiToolBo bo = validBo();
        bo.setUrlTemplate("https://{{host}}.example.com/v1/forecast");
        assertThrows(ServiceException.class, () -> service.insertByBo(bo));
    }

    @Test
    void nonHttpSchemeRejected() {
        AiToolBo bo = validBo();
        bo.setUrlTemplate("file:///etc/passwd");
        assertThrows(ServiceException.class, () -> service.insertByBo(bo));
    }

    @Test
    void customHeaderAuthRequiresKey() {
        AiToolBo bo = validBo();
        bo.setAuthType("2");
        assertThrows(ServiceException.class, () -> service.insertByBo(bo));
    }

    @Test
    void deleteCascadesRel() {
        service.deleteByIds(List.of(1L, 2L));
        verify(relMapper).delete(any(Wrapper.class));
        verify(baseMapper).deleteByIds(anyCollection());
    }

    @Test
    void bindDeduplicatesExisting() {
        AiAppToolRel existing = new AiAppToolRel();
        existing.setToolId(1L);
        when(relMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing));
        when(relMapper.insertBatch(anyCollection())).thenReturn(true);
        Boolean ok = service.bindTools(9L, List.of(1L, 2L));
        assertTrue(ok);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiAppToolRel>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(relMapper).insertBatch(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(2L, captor.getValue().get(0).getToolId());
    }

    @Test
    void updateBlankAuthKeepsOld() {
        when(baseMapper.selectCount(any())).thenReturn(0L);
        AiToolBo bo = validBo();
        bo.setToolId(1L);
        bo.setAuthValue("");
        doReturn(toEntity(bo)).when(service).toEntity(bo);
        ArgumentCaptor<AiTool> captor = ArgumentCaptor.forClass(AiTool.class);
        service.updateByBo(bo);
        verify(baseMapper).updateById(captor.capture());
        assertNull(captor.getValue().getAuthValue());
    }

    @Test
    void testRunDelegatesToExecutor() {
        AiTool tool = new AiTool();
        tool.setToolId(1L);
        tool.setStatus("0");
        when(baseMapper.selectById(1L)).thenReturn(tool);
        ToolExecResult expected = new ToolExecResult(true, 200, "ok", null, 5);
        when(toolExecutor.execute(any(), any(), any())).thenReturn(expected);
        ToolExecResult result = service.testRun(1L, java.util.Map.of("city", "北京"));
        assertEquals(expected, result);
    }

    @Test
    void testRunRejectsDisabledTool() {
        AiTool tool = new AiTool();
        tool.setToolId(1L);
        tool.setStatus("1");
        when(baseMapper.selectById(1L)).thenReturn(tool);
        assertThrows(ServiceException.class, () -> service.testRun(1L, java.util.Map.of()));
        verify(toolExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void methodIsNormalizedToUpperCase() {
        when(baseMapper.selectCount(any())).thenReturn(0L);
        AiToolBo bo = validBo();
        bo.setMethod("get");
        // 桩在调用时求值：validateTool 会先把 bo.method 规范化为大写再转实体
        doAnswer(inv -> toEntity(inv.getArgument(0))).when(service).toEntity(any(AiToolBo.class));
        lenient().when(baseMapper.insert(any(AiTool.class))).thenReturn(1);
        service.insertByBo(bo);
        ArgumentCaptor<AiTool> captor = ArgumentCaptor.forClass(AiTool.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals("GET", captor.getValue().getMethod());
    }
}
