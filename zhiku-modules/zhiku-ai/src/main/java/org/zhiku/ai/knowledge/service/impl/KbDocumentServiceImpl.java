package org.zhiku.ai.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zhiku.ai.knowledge.domain.KbChunk;
import org.zhiku.ai.knowledge.domain.KbDocument;
import org.zhiku.ai.knowledge.domain.vo.KbDocumentVo;
import org.zhiku.ai.knowledge.mapper.KbChunkMapper;
import org.zhiku.ai.knowledge.mapper.KbDocumentMapper;
import org.zhiku.ai.knowledge.service.IKbDocumentService;
import org.zhiku.ai.knowledge.storage.StorageService;
import org.zhiku.common.core.domain.PageResult;
import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.core.utils.StringUtils;
import org.zhiku.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库文档Service业务层处理
 *
 * @author zhiku
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class KbDocumentServiceImpl implements IKbDocumentService {

    private final KbDocumentMapper baseMapper;
    private final KbChunkMapper chunkMapper;
    private final StorageService storageService;

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
        KbDocument doc = baseMapper.selectById(documentId);
        if (doc == null) {
            throw new ServiceException("文档不存在：" + documentId);
        }
        // TODO Task12: Milvus 向量清理（vectorStore.deleteByExpr(dataset.dim, "document_id == " + id)）
        chunkMapper.delete(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocumentId, documentId));
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
