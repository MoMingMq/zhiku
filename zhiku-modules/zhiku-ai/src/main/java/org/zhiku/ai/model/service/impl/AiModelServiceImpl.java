package org.zhiku.ai.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zhiku.ai.model.client.AiModelClientFactory;
import org.zhiku.ai.model.client.RerankClient;
import org.zhiku.ai.model.domain.AiModel;
import org.zhiku.ai.model.domain.bo.AiModelBo;
import org.zhiku.ai.model.domain.vo.AiModelVo;
import org.zhiku.ai.model.domain.vo.ModelTestResult;
import org.zhiku.ai.model.mapper.AiModelMapper;
import org.zhiku.ai.model.service.IAiModelService;
import org.springframework.ai.chat.prompt.Prompt;
import org.zhiku.common.core.domain.PageResult;
import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.core.utils.MapstructUtils;
import org.zhiku.common.core.utils.StringUtils;
import org.zhiku.common.json.utils.JsonUtils;
import org.zhiku.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * AI 模型配置Service业务层处理
 *
 * @author zhiku
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AiModelServiceImpl implements IAiModelService {

    private final AiModelMapper baseMapper;
    private final AiModelClientFactory clientFactory;
    private final RerankClient rerankClient;

    @Override
    public PageResult<AiModelVo> queryPageList(AiModelBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<AiModel> lqw = buildQueryWrapper(bo);
        Page<AiModelVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        result.getRecords().forEach(this::maskKey);
        return PageResult.build(result.getRecords(), result.getTotal());
    }

    @Override
    public List<AiModelVo> queryList(AiModelBo bo) {
        LambdaQueryWrapper<AiModel> lqw = buildQueryWrapper(bo);
        List<AiModelVo> list = baseMapper.selectVoList(lqw);
        list.forEach(this::maskKey);
        return list;
    }

    @Override
    public AiModelVo queryById(Long modelId) {
        AiModelVo vo = baseMapper.selectVoById(modelId);
        if (vo != null) {
            maskKey(vo);
        }
        return vo;
    }

    @Override
    public Boolean insertByBo(AiModelBo bo) {
        validateParams(bo);
        AiModel add = toEntity(bo);
        if ("1".equals(add.getIsDefault())) {
            clearDefaultOf(add.getModelType(), null);
        }
        boolean ok = baseMapper.insert(add) > 0;
        if (ok) {
            bo.setModelId(add.getModelId());
        }
        return ok;
    }

    @Override
    public Boolean updateByBo(AiModelBo bo) {
        validateParams(bo);
        AiModel update = toEntity(bo);
        // apiKey 留空表示不修改（MyBatis-Plus updateById 跳过 null 字段）
        if (StringUtils.isBlank(bo.getApiKey())) {
            update.setApiKey(null);
        }
        if ("1".equals(update.getIsDefault())) {
            clearDefaultOf(update.getModelType(), update.getModelId());
        }
        boolean ok = baseMapper.updateById(update) > 0;
        if (ok) {
            clientFactory.evict(bo.getModelId());
        }
        return ok;
    }

    /**
     * Bo 转 Entity（protected 钩子：单测环境无 Spring 容器，MapstructUtils 不可用时可覆写）
     */
    protected AiModel toEntity(AiModelBo bo) {
        return MapstructUtils.convert(bo, AiModel.class);
    }

    @Override
    public ModelTestResult testConnection(Long modelId) {
        AiModel model = baseMapper.selectById(modelId);
        if (model == null) {
            return new ModelTestResult(false, 0, "模型不存在");
        }
        long start = System.currentTimeMillis();
        try {
            String message = switch (model.getModelType() == null ? "" : model.getModelType()) {
                case "1" -> {
                    clientFactory.getChatModel(modelId).call(new Prompt("你好"));
                    yield "对话模型连通正常";
                }
                case "2" -> {
                    float[] vector = clientFactory.getEmbeddingModel(modelId).embed("连通性测试");
                    yield "向量模型连通正常（维度 " + vector.length + "）";
                }
                case "3" -> {
                    rerankClient.rerank(model, "连通性", java.util.List.of("连通性测试", "无关文本"));
                    yield "重排模型连通正常";
                }
                default -> throw new ServiceException("未知模型类型：" + model.getModelType());
            };
            return new ModelTestResult(true, System.currentTimeMillis() - start, message);
        } catch (Exception e) {
            log.warn("模型连通性测试失败 id={}", modelId, e);
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return new ModelTestResult(false, System.currentTimeMillis() - start,
                StringUtils.substring(reason, 0, 200));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateModelParams(Long modelId, Map<String, Object> params) {
        AiModel model = baseMapper.selectById(modelId);
        if (model == null) {
            throw new ServiceException("模型不存在：" + modelId);
        }
        Map<String, Object> safeParams = params == null ? Map.of() : params;
        safeParams.keySet().forEach(key -> {
            if (StringUtils.isBlank(key)) {
                throw new ServiceException("参数名不能为空");
            }
        });
        LambdaUpdateWrapper<AiModel> luw = new LambdaUpdateWrapper<>();
        luw.eq(AiModel::getModelId, modelId)
            .set(AiModel::getParams, JsonUtils.toJsonString(safeParams));
        boolean ok = baseMapper.update(null, luw) > 0;
        if (ok) {
            clientFactory.evict(modelId);
        }
        return ok;
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        // TODO M2: 被知识库（kb_dataset.embed_model_id）/应用引用中的模型不可删，提示引用清单
        boolean ok = baseMapper.deleteByIds(ids) > 0;
        if (ok) {
            ids.forEach(clientFactory::evict);
        }
        return ok;
    }

    /**
     * 构造查询条件
     */
    private LambdaQueryWrapper<AiModel> buildQueryWrapper(AiModelBo bo) {
        LambdaQueryWrapper<AiModel> lqw = new LambdaQueryWrapper<>();
        lqw.like(StringUtils.isNotBlank(bo.getModelName()), AiModel::getModelName, bo.getModelName());
        lqw.eq(StringUtils.isNotBlank(bo.getModelType()), AiModel::getModelType, bo.getModelType());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), AiModel::getStatus, bo.getStatus());
        lqw.orderByDesc(AiModel::getModelId);
        return lqw;
    }

    /**
     * 业务校验：向量模型必须指定维度；params 必须是合法 JSON
     */
    private void validateParams(AiModelBo bo) {
        if ("2".equals(bo.getModelType()) && (bo.getEmbedDim() == null || bo.getEmbedDim() <= 0)) {
            throw new ServiceException("Embedding 向量模型必须配置向量维度");
        }
        if (StringUtils.isNotBlank(bo.getParams())) {
            try {
                JsonUtils.parseMap(bo.getParams());
            } catch (Exception e) {
                throw new ServiceException("模型参数必须是合法的 JSON 格式");
            }
        }
    }

    /**
     * 默认模型互斥：清除同类型既有默认（excludeId 用于编辑场景排除自身）
     */
    private void clearDefaultOf(String modelType, Long excludeId) {
        LambdaUpdateWrapper<AiModel> luw = new LambdaUpdateWrapper<>();
        luw.eq(AiModel::getModelType, modelType)
            .eq(AiModel::getIsDefault, "1")
            .set(AiModel::getIsDefault, "0");
        if (excludeId != null) {
            luw.ne(AiModel::getModelId, excludeId);
        }
        baseMapper.update(null, luw);
    }

    /**
     * apiKey 掩码：任何出口不回显明文
     */
    private void maskKey(AiModelVo vo) {
        if (StringUtils.isBlank(vo.getApiKey())) {
            vo.setApiKey("");
            vo.setHasKey(false);
        } else {
            String key = vo.getApiKey();
            vo.setApiKey(key.length() <= 3 ? "***" : key.substring(0, 3) + "***");
            vo.setHasKey(true);
        }
    }

}
