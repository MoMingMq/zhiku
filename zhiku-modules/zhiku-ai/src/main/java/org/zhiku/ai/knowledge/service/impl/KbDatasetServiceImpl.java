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
import org.springframework.transaction.annotation.Transactional;

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
    private final org.zhiku.ai.model.mapper.AiModelMapper aiModelMapper;
    private final org.zhiku.ai.knowledge.service.IKbDocumentService documentService;
    private final org.zhiku.ai.knowledge.mapper.KbProblemMapper problemMapper;
    private final org.zhiku.ai.knowledge.mapper.KbProblemChunkRelMapper problemRelMapper;
    private final org.zhiku.ai.app.mapper.AiAppKbRelMapper appKbRelMapper;
    private final org.springframework.beans.factory.ObjectProvider<org.zhiku.ai.knowledge.vector.KbVectorStore> vectorStoreProvider;

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
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        for (Long datasetId : ids) {
            cascadeDelete(datasetId);
        }
        return baseMapper.deleteByIds(ids) > 0;
    }

    /**
     * 级联删除：文档（向量/分块/问题关联/磁盘文件，复用单文档删除链路）→ 问题与关联 → 整库向量表达式兜底 → 解除应用挂载
     */
    private void cascadeDelete(Long datasetId) {
        KbDataset dataset = baseMapper.selectById(datasetId);
        if (dataset == null) {
            return;
        }
        List<KbDocument> documents = documentMapper.selectList(
            new LambdaQueryWrapper<KbDocument>().eq(KbDocument::getDatasetId, datasetId));
        // 处理中保护保留：与异步入库管线竞态会残留孤儿分块与向量
        for (KbDocument doc : documents) {
            if (org.zhiku.ai.knowledge.ingest.DocumentStatus.IN_PROGRESS.contains(doc.getStatus())) {
                throw new ServiceException("知识库下存在处理中的文档（" + doc.getFileName() + "），请等处理结束后再删除知识库");
            }
        }
        for (KbDocument doc : documents) {
            documentService.deleteById(doc.getDocumentId());
        }
        // 问题域：问题与关联（问题向量由下方整库表达式兜底清理）
        problemMapper.delete(new LambdaQueryWrapper<org.zhiku.ai.knowledge.domain.KbProblem>()
            .eq(org.zhiku.ai.knowledge.domain.KbProblem::getDatasetId, datasetId));
        problemRelMapper.delete(new LambdaQueryWrapper<org.zhiku.ai.knowledge.domain.KbProblemChunkRel>()
            .eq(org.zhiku.ai.knowledge.domain.KbProblemChunkRel::getDatasetId, datasetId));
        // 整库向量兜底（kb_id 表达式覆盖问题向量与任何残余；幂等）
        org.zhiku.ai.knowledge.vector.KbVectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore != null && dataset.getEmbedDim() != null) {
            vectorStore.deleteByExpr(dataset.getEmbedDim(), "kb_id == " + datasetId);
        }
        // 解除应用挂载关系
        appKbRelMapper.delete(new LambdaQueryWrapper<org.zhiku.ai.app.domain.AiAppKbRel>()
            .eq(org.zhiku.ai.app.domain.AiAppKbRel::getDatasetId, datasetId));
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
        if (bo.getRerankModelId() != null) {
            var rerankModel = aiModelMapper.selectById(bo.getRerankModelId());
            if (rerankModel == null || !"3".equals(rerankModel.getModelType())) {
                throw new ServiceException("重排模型不存在或类型不是 Rerank");
            }
        }
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
