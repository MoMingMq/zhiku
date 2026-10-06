package org.zhiku.ai.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zhiku.ai.knowledge.domain.KbChunk;
import org.zhiku.ai.knowledge.domain.KbDataset;
import org.zhiku.ai.knowledge.domain.KbDocument;
import org.zhiku.ai.knowledge.domain.KbProblemChunkRel;
import org.zhiku.ai.knowledge.domain.vo.RetrievalHit;
import org.zhiku.ai.knowledge.domain.vo.RetrievalQuery;
import org.zhiku.ai.knowledge.mapper.KbChunkMapper;
import org.zhiku.ai.knowledge.mapper.KbDatasetMapper;
import org.zhiku.ai.knowledge.mapper.KbDocumentMapper;
import org.zhiku.ai.knowledge.mapper.KbProblemChunkRelMapper;
import org.zhiku.ai.knowledge.service.IAiRetrievalService;
import org.zhiku.ai.knowledge.vector.KbVectorStore;
import org.zhiku.ai.model.client.AiModelClientFactory;
import org.zhiku.ai.model.client.RerankClient;
import org.zhiku.ai.model.domain.AiModel;
import org.zhiku.ai.model.domain.vo.AiModelVo;
import org.zhiku.ai.model.mapper.AiModelMapper;
import org.zhiku.ai.model.service.IAiModelRefService;
import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.core.utils.StringUtils;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 检索服务实现：跨库按 embedding 模型分组召回 → 可选重排 → MySQL 补全 → 过滤禁用
 *
 * @author zhiku
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AiRetrievalServiceImpl implements IAiRetrievalService {

    private final KbDatasetMapper datasetMapper;
    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final KbProblemChunkRelMapper problemRelMapper;
    private final AiModelMapper modelMapper;
    private final AiModelClientFactory modelClientFactory;
    private final IAiModelRefService modelRefService;
    private final RerankClient rerankClient;
    /** Milvus 未启用时向量库 bean 不存在：检索明确报错（不静默空结果） */
    private final ObjectProvider<KbVectorStore> vectorStoreProvider;

    @Override
    @Transactional(readOnly = true)
    public List<RetrievalHit> search(RetrievalQuery query) {
        if (query == null || StringUtils.isBlank(query.getText())
            || query.getKbIds() == null || query.getKbIds().isEmpty()) {
            return List.of();
        }
        KbVectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new ServiceException("向量服务不可用（zhiku.ai.milvus.enabled=false）");
        }
        // 知识库按 embedding 模型分组（同组 query 只向量化一次）
        Map<Long, List<KbDataset>> groups = new LinkedHashMap<>();
        for (Long kbId : query.getKbIds()) {
            KbDataset dataset = datasetMapper.selectById(kbId);
            if (dataset == null || dataset.getEmbedDim() == null) {
                continue;
            }
            groups.computeIfAbsent(dataset.getEmbedModelId(), k -> new ArrayList<>()).add(dataset);
        }
        // 各组向量召回并集
        List<KbVectorStore.VectorHit> candidates = new ArrayList<>();
        for (Map.Entry<Long, List<KbDataset>> entry : groups.entrySet()) {
            EmbeddingModel model = modelClientFactory.getEmbeddingModel(entry.getKey());
            float[] queryVector = model.embed(query.getText());
            String filter = "kb_id in [" + entry.getValue().stream()
                .map(KbDataset::getDatasetId)
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";
            candidates.addAll(vectorStore.search(entry.getValue().get(0).getEmbedDim(),
                queryVector, filter, query.getTopK()));
        }
        // 问题命中展开：document_id=0 标识问题向量，按关联关系映射为分段命中
        candidates = expandProblems(candidates);
        if (candidates.isEmpty()) {
            return List.of();
        }
        // 可选重排：候选全文送默认 Rerank 模型，截 topK
        if (query.isRerankEnabled()) {
            candidates = rerank(query, candidates);
        }
        return assemble(candidates, query.getTopK());
    }

    /**
     * 问题命中展开：document_id=0 标识问题向量（chunkId 字段承载 problemId），
     * 命中后按关联关系展开为分段命中，与普通分段命中合并去重（同分段保最高分）
     */
    private List<KbVectorStore.VectorHit> expandProblems(List<KbVectorStore.VectorHit> candidates) {
        List<Long> problemIds = candidates.stream()
            .filter(h -> h.getDocumentId() != null && h.getDocumentId() == KbProblemServiceImpl.PROBLEM_DOCUMENT_FLAG)
            .map(KbVectorStore.VectorHit::getChunkId)
            .toList();
        if (problemIds.isEmpty()) {
            return candidates;
        }
        Map<Long, List<KbProblemChunkRel>> relMap = problemRelMapper.selectList(
                new LambdaQueryWrapper<KbProblemChunkRel>().in(KbProblemChunkRel::getProblemId, problemIds))
            .stream()
            .collect(Collectors.groupingBy(KbProblemChunkRel::getProblemId));
        Map<Long, Float> problemScores = candidates.stream()
            .filter(h -> h.getDocumentId() != null && h.getDocumentId() == KbProblemServiceImpl.PROBLEM_DOCUMENT_FLAG)
            .collect(Collectors.toMap(KbVectorStore.VectorHit::getChunkId, KbVectorStore.VectorHit::getScore, Math::max));
        List<KbVectorStore.VectorHit> expanded = new ArrayList<>();
        for (KbVectorStore.VectorHit hit : candidates) {
            if (hit.getDocumentId() != null && hit.getDocumentId() == KbProblemServiceImpl.PROBLEM_DOCUMENT_FLAG) {
                List<KbProblemChunkRel> rels = relMap.getOrDefault(hit.getChunkId(), List.of());
                for (KbProblemChunkRel rel : rels) {
                    expanded.add(KbVectorStore.VectorHit.builder()
                        .id(hit.getId())
                        .kbId(rel.getDatasetId())
                        .documentId(rel.getDocumentId())
                        .chunkId(rel.getChunkId())
                        .score(problemScores.get(hit.getChunkId()))
                        .build());
                }
            } else {
                expanded.add(hit);
            }
        }
        // 去重：同一分段（可能同时被分段直中与问题展开命中）保最高分
        Map<Long, KbVectorStore.VectorHit> dedup = new java.util.LinkedHashMap<>();
        for (KbVectorStore.VectorHit hit : expanded) {
            dedup.merge(hit.getChunkId(), hit, (a, b) -> a.getScore() >= b.getScore() ? a : b);
        }
        return new ArrayList<>(dedup.values());
    }

    /**
     * 重排（存在默认 Rerank 模型时生效）
     */
    private List<KbVectorStore.VectorHit> rerank(RetrievalQuery query, List<KbVectorStore.VectorHit> candidates) {
        AiModelVo rerankModelVo = modelRefService.getDefaultModel("3");
        if (rerankModelVo == null) {
            log.debug("未配置默认 Rerank 模型，跳过重排");
            return candidates;
        }
        AiModel rerankModel = modelMapper.selectById(rerankModelVo.getModelId());
        Map<Long, String> contents = loadContents(candidates);
        List<String> documents = candidates.stream()
            .map(hit -> contents.getOrDefault(hit.getChunkId(), ""))
            .toList();
        try {
            List<RerankClient.RerankResult> results = rerankClient.rerank(rerankModel, query.getText(), documents);
            List<KbVectorStore.VectorHit> reranked = new ArrayList<>(results.size());
            for (RerankClient.RerankResult result : results) {
                if (result.index() < candidates.size()) {
                    KbVectorStore.VectorHit hit = candidates.get(result.index());
                    hit.setScore((float) result.score());
                    reranked.add(hit);
                }
            }
            return reranked.size() > query.getTopK() ? reranked.subList(0, query.getTopK()) : reranked;
        } catch (Exception e) {
            log.warn("重排失败，回退向量排序：{}", e.getMessage());
            return candidates;
        }
    }

    /**
     * MySQL 补全正文/标题路径/文档名，过滤禁用分块，按分数降序
     */
    private List<RetrievalHit> assemble(List<KbVectorStore.VectorHit> candidates, int topK) {
        Map<Long, String> contents = loadContents(candidates);
        List<RetrievalHit> hits = new ArrayList<>();
        for (KbVectorStore.VectorHit hit : candidates) {
            String content = contents.get(hit.getChunkId());
            if (content == null) {
                continue;
            }
            RetrievalHit r = new RetrievalHit();
            r.setChunkId(hit.getChunkId());
            r.setDocumentId(hit.getDocumentId());
            r.setDatasetId(hit.getKbId());
            r.setScore(hit.getScore());
            hits.add(r);
        }
        if (hits.isEmpty()) {
            return List.of();
        }
        // 批量补全 chunk 元数据与文档名
        List<Long> chunkIds = hits.stream().map(RetrievalHit::getChunkId).toList();
        Map<Long, KbChunk> chunkMap = chunkMapper.selectByIds(chunkIds).stream()
            .collect(Collectors.toMap(KbChunk::getChunkId, c -> c));
        Map<Long, String> docNames = documentMapper.selectByIds(
                hits.stream().map(RetrievalHit::getDocumentId).distinct().toList()).stream()
            .collect(Collectors.toMap(KbDocument::getDocumentId, KbDocument::getFileName));
        List<RetrievalHit> result = new ArrayList<>();
        for (RetrievalHit hit : hits) {
            KbChunk chunk = chunkMap.get(hit.getChunkId());
            if (chunk == null || "1".equals(chunk.getDisabled())) {
                continue;
            }
            hit.setContent(chunk.getContent());
            hit.setHeadingPath(chunk.getHeadingPath());
            hit.setSeq(chunk.getSeq());
            hit.setDocumentName(docNames.get(hit.getDocumentId()));
            result.add(hit);
        }
        result.sort(Comparator.comparing(RetrievalHit::getScore).reversed());
        return result.size() > topK ? result.subList(0, topK) : result;
    }

    /**
     * 候选分块正文（重排输入用）
     */
    private Map<Long, String> loadContents(List<KbVectorStore.VectorHit> candidates) {
        List<Long> chunkIds = candidates.stream()
            .map(KbVectorStore.VectorHit::getChunkId)
            .filter(java.util.Objects::nonNull)
            .toList();
        if (chunkIds.isEmpty()) {
            return Map.of();
        }
        return chunkMapper.selectByIds(chunkIds).stream()
            .collect(Collectors.toMap(KbChunk::getChunkId, KbChunk::getContent));
    }

}
