package org.zhiku.ai.knowledge.service;

import org.zhiku.ai.knowledge.domain.vo.KbChunkVo;
import org.zhiku.common.core.domain.PageResult;
import org.zhiku.common.mybatis.core.page.PageQuery;

import java.util.List;

/**
 * 知识库分段Service接口（分段为可编辑实体：增删改/排序，内容变更自动同步向量）
 *
 * @author zhiku
 */
public interface IKbChunkService {

    /**
     * 查询文档的分段列表
     */
    List<KbChunkVo> queryByDocumentId(Long documentId);

    /**
     * 分页查询文档分段（绑定弹窗滚动分页用）
     *
     * @param searchType title|content 检索字段
     * @param keyword    检索词
     */
    PageResult<KbChunkVo> queryPage(Long documentId, String searchType, String keyword, PageQuery pageQuery);

    /**
     * 切换分段禁用状态（禁用后不参与检索）
     */
    Boolean changeDisabled(Long chunkId, boolean disabled);

    /**
     * 编辑分段（标题/正文；正文变更时重新向量化并覆盖 Milvus 向量）
     */
    Boolean editChunk(Long chunkId, String title, String content);

    /**
     * 新增分段（追加到文档末尾并向量化）
     */
    Long addChunk(Long documentId, String title, String content);

    /**
     * 删除分段（同步删除 Milvus 向量）
     */
    Boolean deleteChunk(Long chunkId);

    /**
     * 分段排序：按传入 chunkId 顺序重排 seq（仅影响展示序，不动向量）
     */
    Boolean reorderChunks(Long documentId, List<Long> chunkIds);

}
