package org.zhiku.ai.model.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.context.support.StaticApplicationContext;
import org.zhiku.ai.model.domain.AiModel;
import org.zhiku.ai.model.mapper.AiModelMapper;
import org.zhiku.common.core.exception.ServiceException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * AiModelRefServiceImpl 单元测试
 *
 * @author zhiku
 */
@ExtendWith(MockitoExtension.class)
class AiModelRefServiceImplTest {

    @Mock
    private AiModelMapper baseMapper;

    @InjectMocks
    private AiModelRefServiceImpl service;

    @BeforeAll
    static void init() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("jsonMapper", JsonMapper.builder().build());
        context.refresh();
        new SpringUtil().setApplicationContext(context);
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
            AiModel.class);
    }

    private AiModel model(String type, Integer dim) {
        AiModel m = new AiModel();
        m.setModelId(1L);
        m.setModelType(type);
        m.setEmbedDim(dim);
        return m;
    }

    @Test
    void queryDimByIdMissingModelThrows() {
        when(baseMapper.selectById(9L)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.queryDimById(9L));
        assertEquals("模型不存在：9", ex.getMessage());
    }

    @Test
    void queryDimByIdNonEmbeddingThrows() {
        when(baseMapper.selectById(1L)).thenReturn(model("1", null));
        assertThrows(ServiceException.class, () -> service.queryDimById(1L));
    }

    @Test
    void queryDimByIdWithoutDimThrows() {
        when(baseMapper.selectById(1L)).thenReturn(model("2", null));
        assertThrows(ServiceException.class, () -> service.queryDimById(1L));
    }

    @Test
    void queryDimByIdReturnsDim() {
        when(baseMapper.selectById(1L)).thenReturn(model("2", 1024));
        assertEquals(1024, service.queryDimById(1L));
    }

}
