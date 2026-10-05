package org.zhiku.ai.knowledge.service;

import org.zhiku.ai.knowledge.domain.vo.KbChunkVo;

import java.util.List;

/**
 * 知识库分块Service接口
 *
 * @author zhiku
 */
public interface IKbChunkService {

    /**
     * 查询文档的分块列表（右表）
     */
    List<KbChunkVo> queryByDocumentId(Long documentId);

    /**
     * 切换分块禁用状态（禁用后不参与检索）
     */
    Boolean changeDisabled(Long chunkId, boolean disabled);

}
