package org.zhiku.ai.model.service;

import org.zhiku.ai.model.domain.vo.AiModelVo;

/**
 * 模型引用服务（供知识库/应用等其他域按需取模型信息，禁直访 Mapper）
 *
 * @author zhiku
 */
public interface IAiModelRefService {

    /**
     * 查询 embedding 模型维度（模型不存在 / 非 embedding 类型 / 未配置维度均抛 ServiceException）
     */
    Integer queryDimById(Long modelId);

    /**
     * 取某类型默认模型（无默认返回 null）
     */
    AiModelVo getDefaultModel(String modelType);

}
