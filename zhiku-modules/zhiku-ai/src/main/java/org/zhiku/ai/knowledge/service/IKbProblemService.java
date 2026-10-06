package org.zhiku.ai.knowledge.service;

import org.zhiku.ai.knowledge.domain.vo.KbProblemVo;

import java.util.Collection;
import java.util.List;

/**
 * 知识库问题Service接口（问题文本向量化参与检索，命中后展开到关联分段）
 *
 * @author zhiku
 */
public interface IKbProblemService {

    /**
     * 查询知识库下问题列表（含关联分段数）
     */
    List<KbProblemVo> queryList(Long datasetId, String keyword);

    /**
     * 查询问题详情（含关联分段ID）
     */
    KbProblemVo queryById(Long problemId);

    /**
     * 新增问题并绑定分段（问题文本向量化入库）
     *
     * @return 问题ID
     */
    Long insertByBo(Long datasetId, String content, List<Long> chunkIds);

    /**
     * 编辑问题（文本变更则重新向量化）并重绑分段
     */
    Boolean updateByBo(Long problemId, String content, List<Long> chunkIds);

    /**
     * 批量删除问题（同步删除向量与关联）
     */
    Boolean deleteByIds(Collection<Long> ids);

}
