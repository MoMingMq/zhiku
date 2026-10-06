package org.zhiku.ai.knowledge.service;

import org.zhiku.ai.knowledge.domain.vo.KbProblemChunkVo;
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
     * 批量新增问题（content 按行拆分，一行一个问题，逐条向量化入库）
     *
     * @return 创建的问题ID列表
     */
    List<Long> insertBatch(Long datasetId, String content);

    /**
     * 绑定分段（追加关联，不重复）
     */
    Boolean bindChunks(Long problemId, List<Long> chunkIds);

    /**
     * 解绑分段
     */
    Boolean unbindChunks(Long problemId, List<Long> chunkIds);

    /**
     * 查询问题关联的分段（含文档名，右侧面板/抽屉展示与解绑用）
     */
    List<KbProblemChunkVo> queryBoundChunks(Long problemId);

    /**
     * 查询分段已绑定的问题（分段编辑弹窗右侧面板展示与解绑用）
     */
    List<KbProblemVo> queryChunkProblems(Long chunkId);

    /**
     * 批量删除问题（同步删除向量与关联）
     */
    Boolean deleteByIds(Collection<Long> ids);

}
