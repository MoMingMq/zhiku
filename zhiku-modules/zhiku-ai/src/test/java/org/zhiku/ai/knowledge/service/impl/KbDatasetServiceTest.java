package org.zhiku.ai.knowledge.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticApplicationContext;
import org.zhiku.ai.knowledge.domain.KbDataset;
import org.zhiku.ai.knowledge.domain.KbDocument;
import org.zhiku.ai.knowledge.domain.bo.KbDatasetBo;
import org.zhiku.ai.knowledge.mapper.KbDatasetMapper;
import org.zhiku.ai.knowledge.mapper.KbDocumentMapper;
import org.zhiku.ai.model.service.IAiModelRefService;
import org.zhiku.common.core.exception.ServiceException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * KbDatasetServiceImpl 单元测试
 *
 * @author zhiku
 */
@ExtendWith(MockitoExtension.class)
class KbDatasetServiceTest {

    @Mock
    private KbDatasetMapper baseMapper;

    @Mock
    private KbDocumentMapper documentMapper;

    @Mock
    private IAiModelRefService modelRefService;

    @Spy
    @InjectMocks
    private KbDatasetServiceImpl service;

    @BeforeAll
    static void init() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("jsonMapper", JsonMapper.builder().build());
        context.refresh();
        new SpringUtil().setApplicationContext(context);
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
            KbDataset.class);
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
            KbDocument.class);
    }

    private KbDatasetBo bo() {
        KbDatasetBo bo = new KbDatasetBo();
        bo.setDatasetName("测试知识库");
        bo.setEmbedModelId(2L);
        return bo;
    }

    private KbDataset entity(KbDatasetBo bo) {
        KbDataset entity = new KbDataset();
        entity.setDatasetId(bo.getDatasetId());
        entity.setDatasetName(bo.getDatasetName());
        entity.setEmbedModelId(bo.getEmbedModelId());
        entity.setChunkParams(bo.getChunkParams());
        return entity;
    }

    @Test
    void insertFetchesDimFromModel() {
        KbDatasetBo bo = bo();
        doReturn(entity(bo)).when(service).toEntity(bo);
        when(modelRefService.queryDimById(2L)).thenReturn(1024);
        when(baseMapper.insert(any(KbDataset.class))).thenReturn(1);
        assertTrue(service.insertByBo(bo));
        org.mockito.ArgumentCaptor<KbDataset> captor = org.mockito.ArgumentCaptor.forClass(KbDataset.class);
        org.mockito.Mockito.verify(baseMapper).insert(captor.capture());
        assertEquals(1024, captor.getValue().getEmbedDim());
    }

    @Test
    void insertInvalidChunkParamsThrows() {
        KbDatasetBo bo = bo();
        bo.setChunkParams("{bad json");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        assertTrue(ex.getMessage().contains("JSON"));
    }

    @Test
    void deleteWithDocumentsRejected() {
        when(documentMapper.selectCount(any())).thenReturn(3L);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.deleteWithValidByIds(java.util.List.of(1L), true));
        assertTrue(ex.getMessage().contains("3 个文档"));
    }

    @Test
    void deleteEmptyDatasetProceeds() {
        when(documentMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.deleteByIds(anyCollection())).thenReturn(1);
        assertTrue(service.deleteWithValidByIds(java.util.List.of(1L), true));
    }

}
