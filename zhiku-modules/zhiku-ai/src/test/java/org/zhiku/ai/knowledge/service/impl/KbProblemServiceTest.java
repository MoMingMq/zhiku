package org.zhiku.ai.knowledge.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.zhiku.ai.knowledge.domain.KbProblem;
import org.zhiku.ai.knowledge.domain.KbProblemChunkRel;
import org.zhiku.ai.knowledge.domain.vo.KbProblemVo;
import org.zhiku.ai.knowledge.mapper.KbChunkMapper;
import org.zhiku.ai.knowledge.mapper.KbDatasetMapper;
import org.zhiku.ai.knowledge.mapper.KbDocumentMapper;
import org.zhiku.ai.knowledge.mapper.KbProblemChunkRelMapper;
import org.zhiku.ai.knowledge.mapper.KbProblemMapper;
import org.zhiku.ai.model.client.AiModelClientFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.zhiku.ai.knowledge.vector.KbVectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * KbProblemServiceImpl 单元测试（分段视角问题绑定查询）
 *
 * @author zhiku
 */
@ExtendWith(MockitoExtension.class)
class KbProblemServiceTest {

    @Mock
    private KbProblemMapper baseMapper;

    @Mock
    private KbProblemChunkRelMapper relMapper;

    @Mock
    private KbChunkMapper chunkMapper;

    @Mock
    private KbDatasetMapper datasetMapper;

    @Mock
    private KbDocumentMapper documentMapper;

    @Mock
    private AiModelClientFactory modelClientFactory;

    @Mock
    private ObjectProvider<KbVectorStore> vectorStoreProvider;

    @InjectMocks
    private KbProblemServiceImpl service;

    @BeforeAll
    static void init() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), KbProblem.class);
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), KbProblemChunkRel.class);
    }

    @Test
    void queryChunkProblemsReturnsEmptyWhenUnbound() {
        when(relMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.queryChunkProblems(1L).isEmpty());
    }

    @Test
    void queryChunkProblemsReturnsBoundProblems() {
        KbProblemChunkRel rel = new KbProblemChunkRel();
        rel.setProblemId(11L);
        rel.setChunkId(1L);
        when(relMapper.selectList(any())).thenReturn(List.of(rel));
        KbProblem problem = new KbProblem();
        problem.setProblemId(11L);
        problem.setDatasetId(2L);
        problem.setContent("防火墙怎么打开");
        when(baseMapper.selectByIds(anyCollection())).thenReturn(List.of(problem));

        List<KbProblemVo> result = service.queryChunkProblems(1L);
        assertEquals(1, result.size());
        assertEquals("防火墙怎么打开", result.get(0).getContent());
        assertEquals(11L, result.get(0).getProblemId());
    }

}
