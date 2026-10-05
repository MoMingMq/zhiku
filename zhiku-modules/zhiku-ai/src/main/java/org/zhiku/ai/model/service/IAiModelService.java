package org.zhiku.ai.model.service;

import org.zhiku.ai.model.domain.bo.AiModelBo;
import org.zhiku.ai.model.domain.vo.AiModelVo;
import org.zhiku.ai.model.domain.vo.ModelTestResult;
import org.zhiku.common.mybatis.core.page.PageQuery;
import org.zhiku.common.core.domain.PageResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * AI 模型配置Service接口
 *
 * @author zhiku
 */
public interface IAiModelService {

    /**
     * 分页查询模型配置列表
     */
    PageResult<AiModelVo> queryPageList(AiModelBo bo, PageQuery pageQuery);

    /**
     * 查询模型配置列表
     */
    List<AiModelVo> queryList(AiModelBo bo);

    /**
     * 根据ID查询模型配置（apiKey 掩码输出）
     */
    AiModelVo queryById(Long modelId);

    /**
     * 新增模型配置
     */
    Boolean insertByBo(AiModelBo bo);

    /**
     * 修改模型配置（apiKey 为空则不更新）
     */
    Boolean updateByBo(AiModelBo bo);

    /**
     * 批量删除模型配置
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 连通性测试：按模型类型发最小请求，返回成功与否与耗时
     */
    ModelTestResult testConnection(Long modelId);

    /**
     * 更新模型参数（Map 承载：参数名→默认值，应用侧按值类型动态生成控件；独立于编辑表单）
     */
    Boolean updateModelParams(Long modelId, Map<String, Object> params);

}
