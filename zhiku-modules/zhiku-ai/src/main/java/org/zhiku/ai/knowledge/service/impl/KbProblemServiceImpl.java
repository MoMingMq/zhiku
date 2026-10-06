package org.zhiku.ai.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zhiku.ai.knowledge.domain.KbChunk;
import org.zhiku.ai.knowledge.domain.KbProblem;
import org.zhiku.ai.knowledge.domain.KbProblemChunkRel;
import org.zhiku.ai.knowledge.domain.vo.KbProblemVo;
import org.zhiku.ai.knowledge.mapper.KbChunkMapper;
import org.zhiku.ai.knowledge.mapper.KbProblemChunkRelMapper;
import org.zhiku.ai.knowledge.mapper.KbProblemMapper;
import org.zhiku.ai.knowledge.service.IKbProblemService;
import org.zhiku.ai.knowledge.vector.KbVectorStore;
import org.zhiku.ai.model.client.AiModelClientFactory;
import org.zhiku.ai.knowledge.domain.KbDataset;
import org.zhiku.ai.knowledge.mapper.KbDatasetMapper;
import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.core.utils.StringUtils;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库问题Service业务层处理
 * <p>问题文本作为独立向量（id=problemId，document_id=0 标识）写入向量库；
 * 检索命中问题后按关联关系展开到分段。文本变更自动重向量化，删除同步清向量。
 *
 * @author zhiku
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class KbProblemServiceImpl implements IKbProblemService {

    /** 问题命中在向量库中的标识：document_id 固定为 0（雪花 ID 不会为 0，天然区分） */
    public static final long PROBLEM_DOCUMENT_FLAG = 0L;

    private final KbProblemMapper baseMapper;
    private final KbProblemChunkRelMapper relMapper;
    private final KbChunkMapper chunkMapper;
    private final KbDatasetMapper datasetMapper;
    private final AiModelClientFactory modelClientFactory;
    private final ObjectProvider<KbVectorStore> vectorStoreProvider;

    @Override
    public List<KbProblemVo> queryList(Long datasetId, String keyword) {
        LambdaQueryWrapper<KbProblem> lqw = new LambdaQueryWrapper<>();
        lqw.eq(KbProblem::getDatasetId, datasetId);
        lqw.like(StringUtils.isNotBlank(keyword), KbProblem::getContent, keyword);
        lqw.orderByDesc(KbProblem::getProblemId);
        List<KbProblem> problems = baseMapper.selectList(lqw);
        if (problems.isEmpty()) {
            return List.of();
        }
        // 关联分段计数
        Map<Long, Long> counts = relMapper.selectList(
                new LambdaQueryWrapper<KbProblemChunkRel>().in(KbProblemChunkRel::getProblemId,
                    problems.stream().map(KbProblem::getProblemId).toList()))
            .stream()
            .collect(Collectors.groupingBy(KbProblemChunkRel::getProblemId, Collectors.counting()));
        return problems.stream().map(p -> {
            KbProblemVo vo = new KbProblemVo();
            vo.setProblemId(p.getProblemId());
            vo.setDatasetId(p.getDatasetId());
            vo.setContent(p.getContent());
            vo.setCreateTime(p.getCreateTime());
            vo.setChunkCount(counts.getOrDefault(p.getProblemId(), 0L));
            return vo;
        }).toList();
    }

    @Override
    public KbProblemVo queryById(Long problemId) {
        KbProblem problem = baseMapper.selectById(problemId);
        if (problem == null) {
            throw new ServiceException("问题不存在：" + problemId);
        }
        KbProblemVo vo = new KbProblemVo();
        vo.setProblemId(problem.getProblemId());
        vo.setDatasetId(problem.getDatasetId());
        vo.setContent(problem.getContent());
        vo.setCreateTime(problem.getCreateTime());
        vo.setChunkIds(relMapper.selectList(new LambdaQueryWrapper<KbProblemChunkRel>()
                .eq(KbProblemChunkRel::getProblemId, problemId))
            .stream().map(KbProblemChunkRel::getChunkId).toList());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertByBo(Long datasetId, String content, List<Long> chunkIds) {
        if (StringUtils.isBlank(content)) {
            throw new ServiceException("问题内容不能为空");
        }
        Long problemId = IdWorker.getId();
        KbProblem problem = new KbProblem();
        problem.setProblemId(problemId);
        problem.setDatasetId(datasetId);
        problem.setContent(content);
        baseMapper.insert(problem);
        bindRel(problem, chunkIds);
        reembed(problem, content);
        return problemId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(Long problemId, String content, List<Long> chunkIds) {
        KbProblem problem = baseMapper.selectById(problemId);
        if (problem == null) {
            throw new ServiceException("问题不存在：" + problemId);
        }
        if (StringUtils.isBlank(content)) {
            throw new ServiceException("问题内容不能为空");
        }
        boolean contentChanged = !content.equals(problem.getContent());
        KbProblem update = new KbProblem();
        update.setProblemId(problemId);
        update.setContent(content);
        baseMapper.updateById(update);
        // 重绑分段
        relMapper.delete(new LambdaQueryWrapper<KbProblemChunkRel>().eq(KbProblemChunkRel::getProblemId, problemId));
        problem.setContent(content);
        bindRel(problem, chunkIds);
        if (contentChanged) {
            reembed(problem, content);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Collection<Long> ids) {
        KbVectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore != null) {
            for (Long problemId : ids) {
                KbProblem problem = baseMapper.selectById(problemId);
                if (problem == null) {
                    continue;
                }
                Integer dim = resolveDim(problem.getDatasetId());
                if (dim != null) {
                    vectorStore.deleteByExpr(dim, "chunk_id == " + problemId);
                }
            }
        }
        relMapper.delete(new LambdaQueryWrapper<KbProblemChunkRel>().in(KbProblemChunkRel::getProblemId, ids));
        return baseMapper.deleteByIds(ids) > 0;
    }

    /**
     * 绑定分段关联（查分段真实归属填充 document_id 冗余字段，校验分段存在且属于本知识库）
     */
    private void bindRel(KbProblem problem, List<Long> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        List<KbChunk> chunks = chunkMapper.selectByIds(chunkIds);
        Map<Long, KbChunk> chunkMap = chunks.stream()
            .collect(Collectors.toMap(KbChunk::getChunkId, c -> c));
        for (Long chunkId : chunkIds) {
            KbChunk chunk = chunkMap.get(chunkId);
            if (chunk == null || !chunk.getDatasetId().equals(problem.getDatasetId())) {
                throw new ServiceException("分段 " + chunkId + " 不存在或不属于当前知识库");
            }
            KbProblemChunkRel rel = new KbProblemChunkRel();
            rel.setRelId(IdWorker.getId());
            rel.setProblemId(problem.getProblemId());
            rel.setChunkId(chunkId);
            rel.setDatasetId(problem.getDatasetId());
            rel.setDocumentId(chunk.getDocumentId());
            relMapper.insert(rel);
        }
    }

    /**
     * 问题文本向量化入库（id=problemId，document_id=0 标识问题）
     */
    private void reembed(KbProblem problem, String content) {
        KbVectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new ServiceException("向量服务未启用（zhiku.ai.milvus.enabled=false），无法写入问题向量");
        }
        KbDataset dataset = datasetMapper.selectById(problem.getDatasetId());
        if (dataset == null || dataset.getEmbedDim() == null) {
            throw new ServiceException("知识库不存在或未配置向量维度");
        }
        vectorStore.ensureCollection(dataset.getEmbedDim());
        EmbeddingModel model = modelClientFactory.getEmbeddingModel(dataset.getEmbedModelId());
        List<float[]> vectors = model.embed(List.of(content));
        vectorStore.upsert(dataset.getEmbedDim(), List.of(KbVectorStore.VectorRow.of(
            String.valueOf(problem.getProblemId()), vectors.get(0),
            problem.getDatasetId(), PROBLEM_DOCUMENT_FLAG, problem.getProblemId())));
    }

    private Integer resolveDim(Long datasetId) {
        KbDataset dataset = datasetMapper.selectById(datasetId);
        return dataset == null ? null : dataset.getEmbedDim();
    }

}
