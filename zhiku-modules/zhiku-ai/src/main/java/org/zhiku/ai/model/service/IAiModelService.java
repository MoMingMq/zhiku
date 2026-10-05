package org.zhiku.ai.model.service;

import org.zhiku.ai.model.domain.bo.AiModelBo;
import org.zhiku.ai.model.domain.vo.AiModelVo;
import org.zhiku.ai.model.domain.vo.ModelParamDef;
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
     * 模型参数定义列表（含渲染组件元数据，应用侧按 inputType 动态渲染控件）
     */
    List<ModelParamDef> queryParams(Long modelId);

    /**
     * 更新模型参数（Map 承载：参数定义列表，独立于编辑表单）
     */
    Boolean updateModelParams(Long modelId, List<ModelParamDef> params);

    /**
     * 参数名→默认值 Map（便捷出口，应用侧拉取配置用）
     */
    Map<String, Object> queryParamValues(Long modelId);

}
