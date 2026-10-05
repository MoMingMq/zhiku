package org.zhiku.ai.knowledge.service;

import org.zhiku.ai.knowledge.domain.bo.KbDatasetBo;
import org.zhiku.ai.knowledge.domain.vo.KbDatasetVo;
import org.zhiku.common.core.domain.PageResult;
import org.zhiku.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 知识库Service接口
 *
 * @author zhiku
 */
public interface IKbDatasetService {

    /**
     * 分页查询知识库列表
     */
    PageResult<KbDatasetVo> queryPageList(KbDatasetBo bo, PageQuery pageQuery);

    /**
     * 查询知识库列表
     */
    List<KbDatasetVo> queryList(KbDatasetBo bo);

    /**
     * 根据ID查询知识库
     */
    KbDatasetVo queryById(Long datasetId);

    /**
     * 新增知识库（维度由绑定的向量模型带出）
     */
    Boolean insertByBo(KbDatasetBo bo);

    /**
     * 修改知识库
     */
    Boolean updateByBo(KbDatasetBo bo);

    /**
     * 批量删除知识库（处理中文档/存在文档时拒绝）
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

}
