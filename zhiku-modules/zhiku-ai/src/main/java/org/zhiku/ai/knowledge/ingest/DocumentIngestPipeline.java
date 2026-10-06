package org.zhiku.ai.knowledge.ingest;

import cn.hutool.core.collection.ListUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zhiku.ai.knowledge.domain.KbChunk;
import org.zhiku.ai.knowledge.domain.KbDataset;
import org.zhiku.ai.knowledge.domain.KbDocument;
import org.zhiku.ai.knowledge.ingest.chunking.ChunkDraft;
import org.zhiku.ai.knowledge.ingest.chunking.ChunkParams;
import org.zhiku.ai.knowledge.ingest.chunking.ChunkingPipeline;
import org.zhiku.ai.knowledge.ingest.chunking.SemanticAggregator;
import org.zhiku.ai.knowledge.ingest.parser.DocumentIR;
import org.zhiku.ai.knowledge.ingest.parser.DocumentParser;
import org.zhiku.ai.knowledge.mapper.KbChunkMapper;
import org.zhiku.ai.knowledge.mapper.KbDatasetMapper;
import org.zhiku.ai.knowledge.mapper.KbDocumentMapper;
import org.zhiku.ai.knowledge.storage.StorageService;
import org.zhiku.ai.knowledge.vector.KbVectorStore;
import org.zhiku.ai.model.client.AiModelClientFactory;
import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.core.utils.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档入库管线（模板方法骨架：解析→分块→落库→向量化→状态回写；任一步失败置 FAILED + error_msg）
 * <p>整文档级重试边界：重跑前 cleanupBefore 清旧分块与向量，不追求 chunk 级补偿。
 *
 * @author zhiku
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class DocumentIngestPipeline {

    private final KbDocumentMapper documentMapper;
    private final KbDatasetMapper datasetMapper;
    private final KbChunkMapper chunkMapper;
    private final List<DocumentParser> parsers;
    private final ChunkingPipeline chunkingPipeline;
    /** Milvus 未启用时向量库 bean 不存在（管线可装配，执行入口检查可用性） */
    private final ObjectProvider<KbVectorStore> vectorStoreProvider;
    private final AiModelClientFactory modelClientFactory;
    private final StorageService storageService;

    /**
     * 向量服务是否可用（上传/重试/重切入口的降级判定）
     */
    public boolean isAvailable() {
        return vectorStoreProvider.getIfAvailable() != null;
    }

    /**
     * embedding 批量大小（供应商批量接口上限内保守取值）
     */
    private static final int EMBED_BATCH = 16;

    /**
     * 执行整文档入库
     */
    public void ingest(Long documentId) {
        KbVectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            fail(documentId, "向量服务未启用（zhiku.ai.milvus.enabled=false）");
            return;
        }
        try {
            KbDocument doc = loadAndTransition(documentId, DocumentStatus.UPLOADED, DocumentStatus.FAILED,
                DocumentStatus.PARSING);
            if (doc == null) {
                return;
            }
            KbDataset dataset = datasetMapper.selectById(doc.getDatasetId());
            if (dataset == null) {
                fail(documentId, "所属知识库不存在");
                return;
            }
            // 1. 解析
            DocumentIR ir = parse(doc);
            transition(documentId, DocumentStatus.CHUNKING);
            // 2. 分块（语义档绑定知识库的 embedding 模型）
            ChunkParams params = ChunkParams.fromJson(dataset.getChunkParams());
            List<ChunkDraft> drafts = chunkingPipeline.run(ir, params, embeddingFn(dataset));
            if (drafts.isEmpty()) {
                fail(documentId, "分块结果为空");
                return;
            }
            // 3. 落分块
            List<KbChunk> chunks = persistChunks(doc, drafts);
            transition(documentId, DocumentStatus.EMBEDDING);
            // 4. 向量化入库
            embedAndUpsert(vectorStore, doc, dataset, drafts, chunks);
            // 5. 完成
            LambdaUpdateWrapper<KbDocument> luw = new LambdaUpdateWrapper<>();
            luw.eq(KbDocument::getDocumentId, documentId)
                .set(KbDocument::getStatus, DocumentStatus.AVAILABLE)
                .set(KbDocument::getCharCount, ir.getCharCount())
                .set(KbDocument::getErrorMsg, null);
            documentMapper.update(null, luw);
        } catch (Exception e) {
            log.error("文档入库失败 id={}", documentId, e);
            fail(documentId, StringUtils.substring(e.getMessage(), 0, 900));
        }
    }

    /**
     * 重试/重切前清理：Milvus 按表达式删旧向量 + 清 kb_chunk
     */
    public void cleanupBefore(Long documentId) {
        KbDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            return;
        }
        KbVectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        KbDataset dataset = datasetMapper.selectById(doc.getDatasetId());
        if (vectorStore != null && dataset != null && dataset.getEmbedDim() != null) {
            vectorStore.deleteByExpr(dataset.getEmbedDim(), "document_id == " + documentId);
        }
        chunkMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KbChunk>()
            .eq(KbChunk::getDocumentId, documentId));
    }

    /**
     * 语义档的向量化函数（绑定知识库 embedding 模型）
     */
    private SemanticAggregator.EmbeddingFn embeddingFn(KbDataset dataset) {
        if (!ChunkParams.fromJson(dataset.getChunkParams()).semanticEnabled()) {
            return null;
        }
        EmbeddingModel model = modelClientFactory.getEmbeddingModel(dataset.getEmbedModelId());
        return texts -> {
            List<float[]> vectors = new ArrayList<>(texts.size());
            for (List<String> batch : ListUtil.partition(texts, EMBED_BATCH)) {
                vectors.addAll(model.embed(batch));
            }
            return vectors;
        };
    }

    private KbDocument loadAndTransition(Long documentId, String fromStatus, String alsoFrom, String toStatus) {
        KbDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            log.warn("文档不存在，跳过入库 id={}", documentId);
            return null;
        }
        if (!fromStatus.equals(doc.getStatus()) && !alsoFrom.equals(doc.getStatus())) {
            log.warn("文档状态非待处理（{}），跳过入库 id={}", doc.getStatus(), documentId);
            return null;
        }
        transition(documentId, toStatus);
        return doc;
    }

    private DocumentIR parse(KbDocument doc) {
        String fileType = doc.getFileType() == null ? "" : doc.getFileType().toLowerCase();
        DocumentParser parser = parsers.stream()
            .filter(p -> p.supports(fileType))
            .findFirst()
            .orElseThrow(() -> new ServiceException("不支持的文档格式：" + fileType));
        try (InputStream in = storageService.read(doc.getFilePath())) {
            return parser.parse(in, doc.getFileName());
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("文档读取失败：" + e.getMessage());
        }
    }

    private List<KbChunk> persistChunks(KbDocument doc, List<ChunkDraft> drafts) {
        List<KbChunk> chunks = new ArrayList<>(drafts.size());
        for (int i = 0; i < drafts.size(); i++) {
            ChunkDraft draft = drafts.get(i);
            KbChunk chunk = new KbChunk();
            chunk.setDatasetId(doc.getDatasetId());
            chunk.setDocumentId(doc.getDocumentId());
            chunk.setSeq(i + 1);
            chunk.setTitle(deriveTitle(draft.getHeadingPath()));
            chunk.setContent(draft.getContent());
            chunk.setHeadingPath(draft.getHeadingPath());
            chunk.setStrategyTag(draft.getStrategyTag());
            chunk.setCharCount(draft.getCharCount());
            chunk.setDisabled("0");
            chunks.add(chunk);
        }
        chunks.forEach(chunkMapper::insert);
        return chunks;
    }

    /**
     * 默认分段标题：标题路径末级（如「第一章>1.1」→ 1.1），无路径返回 null
     */
    private String deriveTitle(String headingPath) {
        if (StringUtils.isBlank(headingPath)) {
            return null;
        }
        String[] parts = headingPath.split(">");
        return parts[parts.length - 1];
    }

    private void embedAndUpsert(KbVectorStore vectorStore, KbDocument doc, KbDataset dataset,
                                List<ChunkDraft> drafts, List<KbChunk> chunks) {
        EmbeddingModel model = modelClientFactory.getEmbeddingModel(dataset.getEmbedModelId());
        int dim = dataset.getEmbedDim();
        vectorStore.ensureCollection(dim);
        for (int start = 0; start < drafts.size(); start += EMBED_BATCH) {
            int end = Math.min(start + EMBED_BATCH, drafts.size());
            List<String> texts = drafts.subList(start, end).stream()
                .map(ChunkDraft::getContent)
                .toList();
            List<float[]> vectors = model.embed(texts);
            List<KbVectorStore.VectorRow> rows = new ArrayList<>(vectors.size());
            for (int i = 0; i < vectors.size(); i++) {
                KbChunk chunk = chunks.get(start + i);
                rows.add(KbVectorStore.VectorRow.of(
                    String.valueOf(chunk.getChunkId()), vectors.get(i),
                    doc.getDatasetId(), doc.getDocumentId(), chunk.getChunkId()));
            }
            vectorStore.upsert(dim, rows);
        }
    }

    private void transition(Long documentId, String status) {
        LambdaUpdateWrapper<KbDocument> luw = new LambdaUpdateWrapper<>();
        luw.eq(KbDocument::getDocumentId, documentId)
            .set(KbDocument::getStatus, status);
        documentMapper.update(null, luw);
    }

    private void fail(Long documentId, String errorMsg) {
        LambdaUpdateWrapper<KbDocument> luw = new LambdaUpdateWrapper<>();
        luw.eq(KbDocument::getDocumentId, documentId)
            .set(KbDocument::getStatus, DocumentStatus.FAILED)
            .set(KbDocument::getErrorMsg, StringUtils.substring(errorMsg, 0, 900));
        documentMapper.update(null, luw);
    }

}
