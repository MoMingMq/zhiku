package org.zhiku.ai.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zhiku.ai.knowledge.domain.KbChunk;
import org.zhiku.ai.knowledge.domain.KbDataset;
import org.zhiku.ai.knowledge.domain.KbDocument;
import org.zhiku.ai.knowledge.domain.KbProblem;
import org.zhiku.ai.knowledge.domain.KbProblemChunkRel;
import org.zhiku.ai.knowledge.domain.vo.KbProblemChunkVo;
import org.zhiku.ai.knowledge.domain.vo.KbProblemVo;
import org.zhiku.ai.knowledge.mapper.KbChunkMapper;
import org.zhiku.ai.knowledge.mapper.KbDatasetMapper;
import org.zhiku.ai.knowledge.mapper.KbDocumentMapper;
import org.zhiku.ai.knowledge.mapper.KbProblemChunkRelMapper;
import org.zhiku.ai.knowledge.mapper.KbProblemMapper;
import org.zhiku.ai.knowledge.service.IKbProblemService;
import org.zhiku.ai.knowledge.vector.KbVectorStore;
import org.zhiku.ai.model.client.AiModelClientFactory;
import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.core.utils.StringUtils;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识库问题Service业务层处理
 * <p>问题文本作为独立向量（id=problemId，document_id=0 标识）写入向量库；
 * 检索命中问题后按关联关系展开到分段。批量创建时一次性批量向量化。
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
    private final KbDocumentMapper documentMapper;
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
        Map<Long, Long> counts = relMapper.selectList(
                new LambdaQueryWrapper<KbProblemChunkRel>().in(KbProblemChunkRel::getProblemId,
                    problems.stream().map(KbProblem::getProblemId).toList()))
            .stream()
            .collect(Collectors.groupingBy(KbProblemChunkRel::getProblemId, Collectors.counting()));
        return problems.stream().map(p -> toVo(p, null, counts.getOrDefault(p.getProblemId(), 0L))).toList();
    }

    @Override
    public KbProblemVo queryById(Long problemId) {
        KbProblem problem = requireProblem(problemId);
        List<Long> chunkIds = relMapper.selectList(new LambdaQueryWrapper<KbProblemChunkRel>()
                .eq(KbProblemChunkRel::getProblemId, problemId))
            .stream().map(KbProblemChunkRel::getChunkId).toList();
        return toVo(problem, chunkIds, (long) chunkIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> insertBatch(Long datasetId, String content) {
        // 按行拆分：一行一个问题，去空白去重
        List<String> lines = Arrays.stream(content == null ? new String[0] : content.split("\n"))
            .map(String::strip)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .toList();
        if (lines.isEmpty()) {
            throw new ServiceException("问题内容不能为空（一行一个问题）");
        }
        List<KbProblem> problems = new ArrayList<>(lines.size());
        for (String line : lines) {
            KbProblem problem = new KbProblem();
            problem.setProblemId(IdWorker.getId());
            problem.setDatasetId(datasetId);
            problem.setContent(line);
            problems.add(problem);
        }
        problems.forEach(baseMapper::insert);
        reembedBatch(datasetId, problems);
        return problems.stream().map(KbProblem::getProblemId).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean bindChunks(Long problemId, List<Long> chunkIds) {
        KbProblem problem = requireProblem(problemId);
        if (chunkIds == null || chunkIds.isEmpty()) {
            return true;
        }
        Set<Long> distinct = new LinkedHashSet<>(chunkIds);
        Map<Long, KbChunk> chunkMap = chunkMapper.selectByIds(distinct).stream()
            .collect(Collectors.toMap(KbChunk::getChunkId, Function.identity()));
        Set<Long> existing = relMapper.selectList(new LambdaQueryWrapper<KbProblemChunkRel>()
                .eq(KbProblemChunkRel::getProblemId, problemId))
            .stream().map(KbProblemChunkRel::getChunkId).collect(Collectors.toSet());
        for (Long chunkId : distinct) {
            KbChunk chunk = chunkMap.get(chunkId);
            if (chunk == null || !chunk.getDatasetId().equals(problem.getDatasetId())) {
                throw new ServiceException("分段 " + chunkId + " 不存在或不属于当前知识库");
            }
            if (existing.contains(chunkId)) {
                continue;
            }
            KbProblemChunkRel rel = new KbProblemChunkRel();
            rel.setRelId(IdWorker.getId());
            rel.setProblemId(problemId);
            rel.setChunkId(chunkId);
            rel.setDatasetId(problem.getDatasetId());
            rel.setDocumentId(chunk.getDocumentId());
            relMapper.insert(rel);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean unbindChunks(Long problemId, List<Long> chunkIds) {
        requireProblem(problemId);
        if (chunkIds == null || chunkIds.isEmpty()) {
            return true;
        }
        relMapper.delete(new LambdaQueryWrapper<KbProblemChunkRel>()
            .eq(KbProblemChunkRel::getProblemId, problemId)
            .in(KbProblemChunkRel::getChunkId, chunkIds));
        return true;
    }

    @Override
    public List<KbProblemChunkVo> queryBoundChunks(Long problemId) {
        requireProblem(problemId);
        List<KbProblemChunkRel> rels = relMapper.selectList(new LambdaQueryWrapper<KbProblemChunkRel>()
            .eq(KbProblemChunkRel::getProblemId, problemId));
        if (rels.isEmpty()) {
            return List.of();
        }
        Map<Long, KbChunk> chunkMap = chunkMapper.selectByIds(
                rels.stream().map(KbProblemChunkRel::getChunkId).toList())
            .stream().collect(Collectors.toMap(KbChunk::getChunkId, Function.identity()));
        Map<Long, String> docNames = documentMapper.selectByIds(
                rels.stream().map(KbProblemChunkRel::getDocumentId).filter(java.util.Objects::nonNull).distinct().toList())
            .stream().collect(Collectors.toMap(KbDocument::getDocumentId, KbDocument::getFileName));
        return rels.stream()
            .map(rel -> {
                KbChunk chunk = chunkMap.get(rel.getChunkId());
                if (chunk == null) {
                    return null;
                }
                KbProblemChunkVo vo = new KbProblemChunkVo();
                vo.setChunkId(chunk.getChunkId());
                vo.setDocumentId(chunk.getDocumentId());
                vo.setDocumentName(docNames.get(chunk.getDocumentId()));
                vo.setTitle(chunk.getTitle());
                vo.setContent(chunk.getContent());
                vo.setSeq(chunk.getSeq());
                vo.setCharCount(chunk.getCharCount());
                return vo;
            })
            .filter(java.util.Objects::nonNull)
            .toList();
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

    private KbProblemVo toVo(KbProblem problem, List<Long> chunkIds, Long chunkCount) {
        KbProblemVo vo = new KbProblemVo();
        vo.setProblemId(problem.getProblemId());
        vo.setDatasetId(problem.getDatasetId());
        vo.setContent(problem.getContent());
        vo.setChunkIds(chunkIds);
        vo.setChunkCount(chunkCount);
        vo.setCreateTime(problem.getCreateTime());
        return vo;
    }

    /**
     * 批量向量化写入（一次 embed 调用，逐条 upsert）
     */
    private void reembedBatch(Long datasetId, List<KbProblem> problems) {
        KbVectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new ServiceException("向量服务未启用（zhiku.ai.milvus.enabled=false），无法写入问题向量");
        }
        KbDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null || dataset.getEmbedDim() == null) {
            throw new ServiceException("知识库不存在或未配置向量维度");
        }
        vectorStore.ensureCollection(dataset.getEmbedDim());
        EmbeddingModel model = modelClientFactory.getEmbeddingModel(dataset.getEmbedModelId());
        List<float[]> vectors = model.embed(problems.stream().map(KbProblem::getContent).toList());
        List<KbVectorStore.VectorRow> rows = new ArrayList<>(problems.size());
        for (int i = 0; i < problems.size(); i++) {
            KbProblem problem = problems.get(i);
            rows.add(KbVectorStore.VectorRow.of(
                String.valueOf(problem.getProblemId()), vectors.get(i),
                datasetId, PROBLEM_DOCUMENT_FLAG, problem.getProblemId()));
        }
        vectorStore.upsert(dataset.getEmbedDim(), rows);
    }

    private Integer resolveDim(Long datasetId) {
        KbDataset dataset = datasetMapper.selectById(datasetId);
        return dataset == null ? null : dataset.getEmbedDim();
    }

    private KbProblem requireProblem(Long problemId) {
        KbProblem problem = baseMapper.selectById(problemId);
        if (problem == null) {
            throw new ServiceException("问题不存在：" + problemId);
        }
        return problem;
    }

}
