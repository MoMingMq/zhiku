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
import static org.mockito.Mockito.verify;
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

    @Mock
    private org.zhiku.ai.knowledge.service.IKbDocumentService documentService;

    @Mock
    private org.zhiku.ai.knowledge.mapper.KbProblemMapper problemMapper;

    @Mock
    private org.zhiku.ai.knowledge.mapper.KbProblemChunkRelMapper problemRelMapper;

    @Mock
    private org.zhiku.ai.app.mapper.AiAppKbRelMapper appKbRelMapper;

    @Mock
    private org.springframework.beans.factory.ObjectProvider<org.zhiku.ai.knowledge.vector.KbVectorStore> vectorStoreProvider;

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
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
            org.zhiku.ai.knowledge.domain.KbProblem.class);
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
            org.zhiku.ai.knowledge.domain.KbProblemChunkRel.class);
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
            org.zhiku.ai.app.domain.AiAppKbRel.class);
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
    void deleteCascadesDocuments() {
        // 新语义：有文档→逐文档复用删除链路级联清库
        org.zhiku.ai.knowledge.domain.KbDataset ds = new org.zhiku.ai.knowledge.domain.KbDataset();
        ds.setDatasetId(1L);
        when(baseMapper.selectById(1L)).thenReturn(ds);
        org.zhiku.ai.knowledge.domain.KbDocument doc = new org.zhiku.ai.knowledge.domain.KbDocument();
        doc.setDocumentId(9L);
        doc.setFileName("a.docx");
        doc.setStatus("4");
        when(documentMapper.selectList(any())).thenReturn(java.util.List.of(doc));
        when(baseMapper.deleteByIds(anyCollection())).thenReturn(1);
        assertTrue(service.deleteWithValidByIds(java.util.List.of(1L), true));
        verify(documentService).deleteById(9L);
    }

    @Test
    void deleteRejectsInProgressDocument() {
        org.zhiku.ai.knowledge.domain.KbDataset ds = new org.zhiku.ai.knowledge.domain.KbDataset();
        ds.setDatasetId(1L);
        when(baseMapper.selectById(1L)).thenReturn(ds);
        org.zhiku.ai.knowledge.domain.KbDocument doc = new org.zhiku.ai.knowledge.domain.KbDocument();
        doc.setDocumentId(9L);
        doc.setFileName("b.docx");
        doc.setStatus("2");
        when(documentMapper.selectList(any())).thenReturn(java.util.List.of(doc));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.deleteWithValidByIds(java.util.List.of(1L), true));
        assertTrue(ex.getMessage().contains("处理中"));
    }

    @Test
    void deleteEmptyDatasetProceeds() {
        org.zhiku.ai.knowledge.domain.KbDataset ds = new org.zhiku.ai.knowledge.domain.KbDataset();
        ds.setDatasetId(1L);
        when(baseMapper.selectById(1L)).thenReturn(ds);
        when(documentMapper.selectList(any())).thenReturn(java.util.List.of());
        when(baseMapper.deleteByIds(anyCollection())).thenReturn(1);
        assertTrue(service.deleteWithValidByIds(java.util.List.of(1L), true));
    }

}
