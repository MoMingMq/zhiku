package org.zhiku.ai.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zhiku.ai.knowledge.domain.KbChunk;
import org.zhiku.ai.knowledge.domain.KbDataset;
import org.zhiku.ai.knowledge.domain.KbDocument;
import org.zhiku.ai.knowledge.domain.KbProblemChunkRel;
import org.zhiku.ai.knowledge.domain.vo.KbChunkVo;
import org.zhiku.ai.knowledge.mapper.KbChunkMapper;
import org.zhiku.ai.knowledge.mapper.KbDatasetMapper;
import org.zhiku.ai.knowledge.mapper.KbDocumentMapper;
import org.zhiku.ai.knowledge.mapper.KbProblemChunkRelMapper;
import org.zhiku.ai.knowledge.service.IKbChunkService;
import org.zhiku.ai.knowledge.vector.KbVectorStore;
import org.zhiku.ai.model.client.AiModelClientFactory;
import org.zhiku.common.core.domain.PageResult;
import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.core.utils.StringUtils;
import org.zhiku.common.mybatis.core.page.PageQuery;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识库分段Service业务层处理
 * <p>分段为可编辑实体：标题/正文可改，正文变更后重新向量化并覆盖 Milvus 向量（id=chunkId 幂等 upsert）；
 * 删除同步清向量；排序仅重排 seq 不动向量。
 *
 * @author zhiku
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class KbChunkServiceImpl implements IKbChunkService {

    private final KbChunkMapper baseMapper;
    private final KbDatasetMapper datasetMapper;
    private final KbDocumentMapper documentMapper;
    private final KbProblemChunkRelMapper problemRelMapper;
    private final AiModelClientFactory modelClientFactory;
    /** Milvus 未启用时向量库不可用：仅允许禁用/排序，增删改内容需向量服务 */
    private final ObjectProvider<KbVectorStore> vectorStoreProvider;

    @Override
    public List<KbChunkVo> queryByDocumentId(Long documentId) {
        LambdaQueryWrapper<KbChunk> lqw = new LambdaQueryWrapper<>();
        lqw.eq(KbChunk::getDocumentId, documentId)
            .orderByAsc(KbChunk::getSeq);
        return baseMapper.selectVoList(lqw);
    }

    @Override
    public PageResult<KbChunkVo> queryPage(Long documentId, String searchType, String keyword, PageQuery pageQuery) {
        LambdaQueryWrapper<KbChunk> lqw = new LambdaQueryWrapper<>();
        lqw.eq(KbChunk::getDocumentId, documentId);
        if (StringUtils.isNotBlank(keyword)) {
            if ("title".equals(searchType)) {
                lqw.and(w -> w.like(KbChunk::getTitle, keyword).or().like(KbChunk::getHeadingPath, keyword));
            } else {
                lqw.like(KbChunk::getContent, keyword);
            }
        }
        lqw.orderByAsc(KbChunk::getSeq);
        Page<KbChunkVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public Boolean changeDisabled(Long chunkId, boolean disabled) {
        KbChunk chunk = requireChunk(chunkId);
        LambdaUpdateWrapper<KbChunk> luw = new LambdaUpdateWrapper<>();
        luw.eq(KbChunk::getChunkId, chunk.getChunkId())
            .set(KbChunk::getDisabled, disabled ? "1" : "0");
        return baseMapper.update(null, luw) > 0;
    }

    @Override
    public Boolean editChunk(Long chunkId, String title, String content) {
        KbChunk chunk = requireChunk(chunkId);
        if (StringUtils.isBlank(content)) {
            throw new ServiceException("分段内容不能为空");
        }
        LambdaUpdateWrapper<KbChunk> luw = new LambdaUpdateWrapper<>();
        luw.eq(KbChunk::getChunkId, chunkId)
            .set(KbChunk::getTitle, StringUtils.blankToDefault(title, null))
            .set(KbChunk::getContent, content)
            .set(KbChunk::getCharCount, content.length());
        boolean ok = baseMapper.update(null, luw) > 0;
        if (ok && !content.equals(chunk.getContent())) {
            // 正文变更：重新向量化并覆盖同 id 向量
            reembed(chunk, content);
        }
        return ok;
    }

    @Override
    public Long addChunk(Long documentId, String title, String content) {
        KbDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new ServiceException("文档不存在：" + documentId);
        }
        if (StringUtils.isBlank(content)) {
            throw new ServiceException("分段内容不能为空");
        }
        Long count = baseMapper.selectCount(
            new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocumentId, documentId));
        int nextSeq = (count == null ? 0 : count.intValue()) + 1;

        KbChunk chunk = new KbChunk();
        chunk.setChunkId(IdWorker.getId());
        chunk.setDatasetId(doc.getDatasetId());
        chunk.setDocumentId(documentId);
        chunk.setSeq(nextSeq);
        chunk.setTitle(StringUtils.blankToDefault(title, null));
        chunk.setContent(content);
        chunk.setCharCount(content.length());
        chunk.setDisabled("0");
        baseMapper.insert(chunk);
        reembed(chunk, content);
        return chunk.getChunkId();
    }

    @Override
    public Boolean deleteChunk(Long chunkId) {
        KbChunk chunk = requireChunk(chunkId);
        KbVectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore != null) {
            Integer dim = resolveDim(chunk.getDatasetId());
            if (dim != null) {
                vectorStore.deleteByExpr(dim, "chunk_id == " + chunkId);
            }
        }
        // 同步清理问题关联
        problemRelMapper.delete(new LambdaQueryWrapper<KbProblemChunkRel>()
            .eq(KbProblemChunkRel::getChunkId, chunkId));
        return baseMapper.deleteById(chunkId) > 0;
    }

    @Override
    public Boolean reorderChunks(Long documentId, List<Long> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            throw new ServiceException("分段顺序不能为空");
        }
        Map<Long, KbChunk> chunkMap = baseMapper.selectByIds(chunkIds).stream()
            .collect(Collectors.toMap(KbChunk::getChunkId, Function.identity()));
        for (int i = 0; i < chunkIds.size(); i++) {
            Long chunkId = chunkIds.get(i);
            KbChunk chunk = chunkMap.get(chunkId);
            if (chunk == null || !chunk.getDocumentId().equals(documentId)) {
                throw new ServiceException("分段 " + chunkId + " 不属于该文档");
            }
            LambdaUpdateWrapper<KbChunk> luw = new LambdaUpdateWrapper<>();
            luw.eq(KbChunk::getChunkId, chunkId)
                .set(KbChunk::getSeq, i + 1);
            baseMapper.update(null, luw);
        }
        return true;
    }

    /**
     * 向量化并写入/覆盖向量（id=chunkId 幂等）
     */
    private void reembed(KbChunk chunk, String content) {
        KbVectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new ServiceException("向量服务未启用（zhiku.ai.milvus.enabled=false），无法更新分段向量");
        }
        KbDataset dataset = datasetMapper.selectById(chunk.getDatasetId());
        if (dataset == null || dataset.getEmbedDim() == null) {
            throw new ServiceException("知识库不存在或未配置向量维度");
        }
        vectorStore.ensureCollection(dataset.getEmbedDim());
        EmbeddingModel model = modelClientFactory.getEmbeddingModel(dataset.getEmbedModelId());
        List<float[]> vectors = model.embed(List.of(content));
        Long chunkId = chunk.getChunkId() != null ? chunk.getChunkId() : IdWorker.getId();
        vectorStore.upsert(dataset.getEmbedDim(), List.of(KbVectorStore.VectorRow.of(
            String.valueOf(chunkId), vectors.get(0), chunk.getDatasetId(), chunk.getDocumentId(), chunkId)));
    }

    private Integer resolveDim(Long datasetId) {
        KbDataset dataset = datasetMapper.selectById(datasetId);
        return dataset == null ? null : dataset.getEmbedDim();
    }

    private KbChunk requireChunk(Long chunkId) {
        KbChunk chunk = baseMapper.selectById(chunkId);
        if (chunk == null) {
            throw new ServiceException("分段不存在：" + chunkId);
        }
        return chunk;
    }

}
