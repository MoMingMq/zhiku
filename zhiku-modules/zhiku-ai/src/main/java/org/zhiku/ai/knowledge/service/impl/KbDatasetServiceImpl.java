package org.zhiku.ai.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zhiku.ai.knowledge.domain.KbDataset;
import org.zhiku.ai.knowledge.domain.KbDocument;
import org.zhiku.ai.knowledge.domain.bo.KbDatasetBo;
import org.zhiku.ai.knowledge.domain.vo.KbDatasetVo;
import org.zhiku.ai.knowledge.ingest.DocumentStatus;
import org.zhiku.ai.knowledge.mapper.KbDatasetMapper;
import org.zhiku.ai.knowledge.mapper.KbDocumentMapper;
import org.zhiku.ai.knowledge.service.IKbDatasetService;
import org.zhiku.ai.model.service.IAiModelRefService;
import org.zhiku.common.core.domain.PageResult;
import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.core.utils.MapstructUtils;
import org.zhiku.common.core.utils.StringUtils;
import org.zhiku.common.json.utils.JsonUtils;
import org.zhiku.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 知识库Service业务层处理
 *
 * @author zhiku
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class KbDatasetServiceImpl implements IKbDatasetService {

    private final KbDatasetMapper baseMapper;
    private final KbDocumentMapper documentMapper;
    private final IAiModelRefService modelRefService;

    @Override
    public PageResult<KbDatasetVo> queryPageList(KbDatasetBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<KbDataset> lqw = buildQueryWrapper(bo);
        Page<KbDatasetVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(result.getRecords(), result.getTotal());
    }

    @Override
    public List<KbDatasetVo> queryList(KbDatasetBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public KbDatasetVo queryById(Long datasetId) {
        return baseMapper.selectVoById(datasetId);
    }

    @Override
    public Boolean insertByBo(KbDatasetBo bo) {
        validate(bo);
        KbDataset add = toEntity(bo);
        // 维度由绑定的向量模型带出（M1 契约：非 embedding 模型/未配置维度均抛错）
        add.setEmbedDim(modelRefService.queryDimById(bo.getEmbedModelId()));
        boolean ok = baseMapper.insert(add) > 0;
        if (ok) {
            bo.setDatasetId(add.getDatasetId());
        }
        return ok;
    }

    @Override
    public Boolean updateByBo(KbDatasetBo bo) {
        validate(bo);
        KbDataset update = toEntity(bo);
        // 向量模型与维度一经创建禁改（已有分段的向量维度与其绑定，改绑会导致向量不可用）
        update.setEmbedModelId(null);
        update.setEmbedDim(null);
        return baseMapper.updateById(update) > 0;
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        // 删除保护：存在任何未删除文档（含处理中）即拒绝——先删空文档再删库
        LambdaQueryWrapper<KbDocument> lqw = new LambdaQueryWrapper<>();
        lqw.in(KbDocument::getDatasetId, ids);
        Long docCount = documentMapper.selectCount(lqw);
        if (docCount != null && docCount > 0) {
            throw new ServiceException("知识库下存在 " + docCount + " 个文档，请先删除全部文档后再删除知识库");
        }
        // TODO M4: 被应用（ai_app_kb_rel）引用中的知识库不可删，提示引用清单
        return baseMapper.deleteByIds(ids) > 0;
    }

    /**
     * 构造查询条件
     */
    private LambdaQueryWrapper<KbDataset> buildQueryWrapper(KbDatasetBo bo) {
        LambdaQueryWrapper<KbDataset> lqw = new LambdaQueryWrapper<>();
        lqw.like(StringUtils.isNotBlank(bo.getDatasetName()), KbDataset::getDatasetName, bo.getDatasetName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), KbDataset::getStatus, bo.getStatus());
        lqw.orderByDesc(KbDataset::getDatasetId);
        return lqw;
    }

    /**
     * 业务校验：chunkParams 必须是合法 JSON
     */
    private void validate(KbDatasetBo bo) {
        if (StringUtils.isNotBlank(bo.getChunkParams())) {
            try {
                JsonUtils.parseMap(bo.getChunkParams());
            } catch (Exception e) {
                throw new ServiceException("分块参数必须是合法的 JSON 格式");
            }
        }
    }

    /**
     * Bo 转 Entity（protected 钩子：单测环境无 Spring 容器时可覆写）
     */
    protected KbDataset toEntity(KbDatasetBo bo) {
        return MapstructUtils.convert(bo, KbDataset.class);
    }

}
