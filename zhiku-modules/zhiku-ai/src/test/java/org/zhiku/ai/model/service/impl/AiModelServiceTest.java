package org.zhiku.ai.model.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticApplicationContext;
import org.zhiku.ai.model.domain.AiModel;
import org.zhiku.ai.model.domain.bo.AiModelBo;
import org.zhiku.ai.model.domain.vo.AiModelVo;
import org.zhiku.ai.model.mapper.AiModelMapper;
import org.zhiku.common.core.exception.ServiceException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiModelServiceImpl 单元测试
 * <p>JsonUtils 静态初始化依赖 Spring 容器：@BeforeAll 向 hutool SpringUtil 注册最小 StaticApplicationContext（含真实 ObjectMapper）；
 * MapstructUtils 依赖 mapstruct Converter 容器 bean，通过覆写 {@code toEntity} 钩子绕开。
 *
 * @author zhiku
 */
@ExtendWith(MockitoExtension.class)
class AiModelServiceTest {

    @Mock
    private AiModelMapper baseMapper;

    @Mock
    private org.zhiku.ai.model.client.AiModelClientFactory clientFactory;

    @Mock
    private org.zhiku.ai.model.client.RerankClient rerankClient;

    @Spy
    @InjectMocks
    private AiModelServiceImpl service;

    @BeforeAll
    static void initSpringUtil() {
        StaticApplicationContext context = new StaticApplicationContext();
        // SB4 走 Jackson3（tools.jackson），JsonUtils 静态块按类型取 JsonMapper
        context.getBeanFactory().registerSingleton("jsonMapper", tools.jackson.databind.json.JsonMapper.builder().build());
        context.refresh();
        // hutool SpringUtil 为 ApplicationContextAware 实例回调，静态字段由实例方法写入
        new SpringUtil().setApplicationContext(context);
        // 纯单测无 MyBatis-Plus 启动流程，手动初始化 AiModel 的 lambda 列缓存（LambdaWrapper 依赖）
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
            AiModel.class);
    }

    /**
     * 构造 convert stub 用的目标 Entity（手动映射，绕开容器依赖）
     */
    private AiModel toEntity(AiModelBo bo) {
        AiModel entity = new AiModel();
        entity.setModelId(bo.getModelId());
        entity.setModelName(bo.getModelName());
        entity.setModelType(bo.getModelType());
        entity.setBaseUrl(bo.getBaseUrl());
        entity.setModelCode(bo.getModelCode());
        entity.setApiKey(bo.getApiKey());
        entity.setEmbedDim(bo.getEmbedDim());
        entity.setParams(bo.getParams());
        entity.setIsDefault(bo.getIsDefault());
        return entity;
    }

    @Test
    void insertEmbeddingWithoutDimThrows() {
        AiModelBo bo = new AiModelBo();
        bo.setModelName("测试向量模型");
        bo.setModelType("2");
        bo.setBaseUrl("https://api.example.com");
        bo.setModelCode("text-embedding-v3");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        assertTrue(ex.getMessage().contains("向量维度"));
    }

    @Test
    void insertInvalidParamsJsonThrows() {
        AiModelBo bo = new AiModelBo();
        bo.setModelName("测试模型");
        bo.setModelType("1");
        bo.setBaseUrl("https://api.example.com");
        bo.setModelCode("deepseek-chat");
        bo.setParams("{invalid json");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        assertTrue(ex.getMessage().contains("JSON"));
    }

    @Test
    void insertDefaultClearsPreviousDefault() {
        AiModelBo bo = new AiModelBo();
        bo.setModelName("默认对话模型");
        bo.setModelType("1");
        bo.setBaseUrl("https://api.example.com");
        bo.setModelCode("deepseek-chat");
        bo.setIsDefault("1");
        doReturn(toEntity(bo)).when(service).toEntity(bo);
        when(baseMapper.insert(any(AiModel.class))).thenReturn(1);
        service.insertByBo(bo);
        verify(baseMapper).update(isNull(), any());
    }

    @Test
    void updateBlankKeyKeepsOldKey() {
        AiModelBo bo = new AiModelBo();
        bo.setModelId(1L);
        bo.setModelName("改名");
        bo.setModelType("1");
        bo.setBaseUrl("https://api.example.com");
        bo.setModelCode("deepseek-chat");
        bo.setApiKey("");
        doReturn(toEntity(bo)).when(service).toEntity(bo);
        when(baseMapper.updateById(any(AiModel.class))).thenReturn(1);
        service.updateByBo(bo);
        ArgumentCaptor<AiModel> captor = ArgumentCaptor.forClass(AiModel.class);
        verify(baseMapper).updateById(captor.capture());
        assertNull(captor.getValue().getApiKey());
    }

    @Test
    void queryByIdMasksApiKey() {
        AiModelVo vo = new AiModelVo();
        vo.setModelId(1L);
        vo.setApiKey("sk-abcdef123456");
        when(baseMapper.selectVoById(1L)).thenReturn(vo);
        AiModelVo result = service.queryById(1L);
        assertEquals("sk-***", result.getApiKey());
        assertEquals(true, result.getHasKey());
    }

    @Test
    void queryByIdShortKeyMaskedFully() {
        AiModelVo vo = new AiModelVo();
        vo.setModelId(2L);
        vo.setApiKey("ab");
        when(baseMapper.selectVoById(2L)).thenReturn(vo);
        assertEquals("***", service.queryById(2L).getApiKey());
    }

    @Test
    void deleteCallsMapper() {
        when(baseMapper.deleteByIds(anyCollection())).thenReturn(1);
        assertTrue(service.deleteWithValidByIds(java.util.List.of(1L), true));
        verify(baseMapper).deleteByIds(anyCollection());
    }

    @Test
    void updateModelParamsBlankNameThrows() {
        when(baseMapper.selectById(1L)).thenReturn(new AiModel());
        org.zhiku.ai.model.domain.vo.ModelParamDef def = new org.zhiku.ai.model.domain.vo.ModelParamDef();
        def.setName(" ");
        def.setInputType("input");
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.updateModelParams(1L, java.util.List.of(def)));
        assertTrue(ex.getMessage().contains("参数名"));
    }

    @Test
    void updateModelParamsDuplicateNameThrows() {
        when(baseMapper.selectById(1L)).thenReturn(new AiModel());
        org.zhiku.ai.model.domain.vo.ModelParamDef a = new org.zhiku.ai.model.domain.vo.ModelParamDef();
        a.setName("temperature");
        a.setInputType("slider");
        a.setMin(0.0);
        a.setMax(2.0);
        org.zhiku.ai.model.domain.vo.ModelParamDef b = new org.zhiku.ai.model.domain.vo.ModelParamDef();
        b.setName("temperature");
        b.setInputType("input");
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.updateModelParams(1L, java.util.List.of(a, b)));
        assertTrue(ex.getMessage().contains("重复"));
    }

    @Test
    void updateModelParamsSliderWithoutRangeThrows() {
        when(baseMapper.selectById(1L)).thenReturn(new AiModel());
        org.zhiku.ai.model.domain.vo.ModelParamDef def = new org.zhiku.ai.model.domain.vo.ModelParamDef();
        def.setName("temperature");
        def.setInputType("slider");
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.updateModelParams(1L, java.util.List.of(def)));
        assertTrue(ex.getMessage().contains("滑块"));
    }

    @Test
    void updateModelParamsMissingModelThrows() {
        when(baseMapper.selectById(9L)).thenReturn(null);
        assertThrows(ServiceException.class,
            () -> service.updateModelParams(9L, java.util.List.of(new org.zhiku.ai.model.domain.vo.ModelParamDef())));
    }

    @Test
    void updateModelParamsEjectsCache() {
        AiModel model = new AiModel();
        model.setModelId(1L);
        when(baseMapper.selectById(1L)).thenReturn(model);
        when(baseMapper.update(isNull(), any())).thenReturn(1);
        org.zhiku.ai.model.domain.vo.ModelParamDef def = new org.zhiku.ai.model.domain.vo.ModelParamDef();
        def.setName("temperature");
        def.setInputType("slider");
        def.setMin(0.0);
        def.setMax(2.0);
        assertTrue(service.updateModelParams(1L, java.util.List.of(def)));
        verify(clientFactory).evict(1L);
    }

}
