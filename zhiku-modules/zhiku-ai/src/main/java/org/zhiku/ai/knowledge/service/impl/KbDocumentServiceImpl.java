package org.zhiku.ai.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zhiku.ai.knowledge.domain.KbChunk;
import org.zhiku.ai.knowledge.domain.KbDataset;
import org.zhiku.ai.knowledge.domain.KbProblemChunkRel;
import org.zhiku.ai.knowledge.domain.KbDocument;
import org.zhiku.ai.knowledge.domain.vo.KbDocumentVo;
import org.zhiku.ai.knowledge.ingest.DocumentIngestPipeline;
import org.zhiku.ai.knowledge.ingest.DocumentStatus;
import org.zhiku.ai.knowledge.ingest.event.DocumentIngestEvent;
import org.zhiku.ai.knowledge.mapper.KbChunkMapper;
import org.zhiku.ai.knowledge.mapper.KbDatasetMapper;
import org.zhiku.ai.knowledge.mapper.KbProblemChunkRelMapper;
import org.zhiku.ai.knowledge.mapper.KbDocumentMapper;
import org.zhiku.ai.knowledge.service.IKbDocumentService;
import org.zhiku.ai.knowledge.storage.StorageService;
import org.zhiku.common.core.domain.PageResult;
import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.core.utils.StringUtils;
import org.zhiku.common.mybatis.core.page.PageQuery;
import org.zhiku.common.core.utils.SpringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * 知识库文档Service业务层处理
 *
 * @author zhiku
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class KbDocumentServiceImpl implements IKbDocumentService {

    /** 支持的文档格式白名单 */
    private static final Set<String> ALLOWED_TYPES = Set.of("pdf", "docx", "xlsx", "md", "markdown", "txt", "html");

    /** 单文件上限 100MB */
    private static final long MAX_SIZE = 100L * 1024 * 1024;

    private final KbDocumentMapper baseMapper;
    private final KbDatasetMapper datasetMapper;
    private final KbChunkMapper chunkMapper;
    private final KbProblemChunkRelMapper problemRelMapper;
    private final StorageService storageService;
    /** Milvus 未启用时 pipeline 不存在（上传/重试/重切降级报错，查询不受影响） */
    private final ObjectProvider<DocumentIngestPipeline> pipelineProvider;

    @Override
    public Long upload(Long datasetId, MultipartFile file) {
        KbDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new ServiceException("知识库不存在：" + datasetId);
        }
        requirePipeline();
        String fileName = file.getOriginalFilename();
        if (StringUtils.isBlank(fileName)) {
            throw new ServiceException("文件名不能为空");
        }
        String fileType = StringUtils.substringAfterLast(fileName, ".").toLowerCase();
        if (!ALLOWED_TYPES.contains(fileType)) {
            throw new ServiceException("不支持的文档格式：" + fileType + "（支持 pdf/docx/xlsx/md/txt/html）");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ServiceException("文件超过 100MB 上限");
        }
        Long documentId = IdWorker.getId();
        try (InputStream in = file.getInputStream()) {
            String storageKey = storageService.store(datasetId, documentId, fileName, in);
            KbDocument doc = new KbDocument();
            doc.setDocumentId(documentId);
            doc.setDatasetId(datasetId);
            doc.setFileName(fileName);
            doc.setFilePath(storageKey);
            doc.setFileSize(file.getSize());
            doc.setFileType(fileType);
            doc.setStatus(DocumentStatus.UPLOADED);
            baseMapper.insert(doc);
        } catch (IOException e) {
            throw new ServiceException("文件读取失败：" + e.getMessage());
        }
        SpringUtils.context().publishEvent(new DocumentIngestEvent(documentId));
        return documentId;
    }

    @Override
    public Boolean retry(Long documentId) {
        KbDocument doc = requireDocument(documentId);
        if (!DocumentStatus.FAILED.equals(doc.getStatus())) {
            throw new ServiceException("仅失败状态的文档可重试");
        }
        return reingestInternal(doc);
    }

    @Override
    public Boolean reingest(Long documentId) {
        KbDocument doc = requireDocument(documentId);
        if (!DocumentStatus.AVAILABLE.equals(doc.getStatus()) && !DocumentStatus.FAILED.equals(doc.getStatus())) {
            throw new ServiceException("仅可用/失败状态的文档可重切");
        }
        return reingestInternal(doc);
    }

    private Boolean reingestInternal(KbDocument doc) {
        DocumentIngestPipeline pipeline = requirePipeline();
        pipeline.cleanupBefore(doc.getDocumentId());
        LambdaUpdateWrapper<KbDocument> luw = new LambdaUpdateWrapper<>();
        luw.eq(KbDocument::getDocumentId, doc.getDocumentId())
            .set(KbDocument::getStatus, DocumentStatus.UPLOADED)
            .set(KbDocument::getErrorMsg, null);
        boolean ok = baseMapper.update(null, luw) > 0;
        if (ok) {
            SpringUtils.context().publishEvent(new DocumentIngestEvent(doc.getDocumentId()));
        }
        return ok;
    }

    private DocumentIngestPipeline requirePipeline() {
        DocumentIngestPipeline pipeline = pipelineProvider.getIfAvailable();
        if (pipeline == null || !pipeline.isAvailable()) {
            throw new ServiceException("向量服务未启用（zhiku.ai.milvus.enabled=false），无法处理文档");
        }
        return pipeline;
    }

    private KbDocument requireDocument(Long documentId) {
        KbDocument doc = baseMapper.selectById(documentId);
        if (doc == null) {
            throw new ServiceException("文档不存在：" + documentId);
        }
        return doc;
    }

    @Override
    public PageResult<KbDocumentVo> queryPageList(Long datasetId, String fileName, String status, PageQuery pageQuery) {
        LambdaQueryWrapper<KbDocument> lqw = new LambdaQueryWrapper<>();
        lqw.eq(KbDocument::getDatasetId, datasetId);
        lqw.like(StringUtils.isNotBlank(fileName), KbDocument::getFileName, fileName);
        lqw.eq(StringUtils.isNotBlank(status), KbDocument::getStatus, status);
        lqw.orderByDesc(KbDocument::getDocumentId);
        Page<KbDocumentVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(result.getRecords(), result.getTotal());
    }

    @Override
    public KbDocumentVo queryById(Long documentId) {
        return baseMapper.selectVoById(documentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteById(Long documentId) {
        KbDocument doc = requireDocument(documentId);
        // 向量清理（Milvus 未启用时仅清 MySQL 与文件）
        DocumentIngestPipeline pipeline = pipelineProvider.getIfAvailable();
        if (pipeline != null) {
            pipeline.cleanupBefore(documentId);
        } else {
            chunkMapper.delete(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocumentId, documentId));
        }
        // 同步清理问题关联
        problemRelMapper.delete(new LambdaQueryWrapper<KbProblemChunkRel>()
            .eq(KbProblemChunkRel::getDocumentId, documentId));
        boolean ok = baseMapper.deleteById(documentId) > 0;
        if (ok) {
            storageService.delete(doc.getFilePath());
        }
        return ok;
    }

    @Override
    public DocumentStat queryStat(Long datasetId) {
        Long docCount = baseMapper.selectCount(
            new LambdaQueryWrapper<KbDocument>().eq(KbDocument::getDatasetId, datasetId));
        Long chunkCount = chunkMapper.selectCount(
            new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDatasetId, datasetId));
        Long availableCount = baseMapper.selectCount(
            new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getDatasetId, datasetId)
                .eq(KbDocument::getStatus, org.zhiku.ai.knowledge.ingest.DocumentStatus.AVAILABLE));
        return new DocumentStat(docCount == null ? 0 : docCount, chunkCount == null ? 0 : chunkCount,
            availableCount == null ? 0 : availableCount);
    }

}
