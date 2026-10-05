package org.zhiku.ai.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.zhiku.ai.model.domain.AiModel;
import org.zhiku.ai.model.domain.vo.AiModelVo;
import org.zhiku.ai.model.mapper.AiModelMapper;
import org.zhiku.ai.model.service.IAiModelRefService;
import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模型引用服务实现
 *
 * @author zhiku
 */
@RequiredArgsConstructor
@Service
public class AiModelRefServiceImpl implements IAiModelRefService {

    private final AiModelMapper baseMapper;

    @Override
    @Transactional(readOnly = true)
    public Integer queryDimById(Long modelId) {
        AiModel model = baseMapper.selectById(modelId);
        if (model == null) {
            throw new ServiceException("模型不存在：" + modelId);
        }
        if (!"2".equals(model.getModelType())) {
            throw new ServiceException("模型 " + modelId + " 不是 Embedding 类型，无法提供向量维度");
        }
        if (model.getEmbedDim() == null || model.getEmbedDim() <= 0) {
            throw new ServiceException("模型 " + modelId + " 未配置向量维度");
        }
        return model.getEmbedDim();
    }

    @Override
    @Transactional(readOnly = true)
    public AiModelVo getDefaultModel(String modelType) {
        if (StringUtils.isBlank(modelType)) {
            return null;
        }
        LambdaQueryWrapper<AiModel> lqw = new LambdaQueryWrapper<>();
        lqw.eq(AiModel::getModelType, modelType)
            .eq(AiModel::getIsDefault, "1")
            .orderByDesc(AiModel::getModelId)
            .last("limit 1");
        return baseMapper.selectVoOne(lqw);
    }

}
