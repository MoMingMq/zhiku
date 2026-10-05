package org.zhiku.ai.knowledge.service;

import org.zhiku.ai.knowledge.domain.vo.KbDocumentVo;
import org.zhiku.common.core.domain.PageResult;
import org.zhiku.common.mybatis.core.page.PageQuery;

import java.util.List;

/**
 * 知识库文档Service接口（上传/重试/重切接口由 Task 12 的入库管线补充）
 *
 * @author zhiku
 */
public interface IKbDocumentService {

    /**
     * 分页查询文档列表（左树：按知识库过滤）
     */
    PageResult<KbDocumentVo> queryPageList(Long datasetId, String fileName, String status, PageQuery pageQuery);

    /**
     * 根据ID查询文档
     */
    KbDocumentVo queryById(Long documentId);

    /**
     * 删除文档（含分块与向量清理，Task 12 接入向量侧后补全链路）
     */
    Boolean deleteById(Long documentId);

    /**
     * 查询知识库下文档统计（文档数/分块数）
     */
    DocumentStat queryStat(Long datasetId);

    /**
     * 知识库统计
     */
    record DocumentStat(Long documentCount, Long chunkCount, Long availableCount) {
    }

}
