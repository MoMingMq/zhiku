package org.zhiku.ai.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.zhiku.ai.knowledge.domain.KbChunk;
import org.zhiku.ai.knowledge.domain.KbDataset;
import org.zhiku.ai.knowledge.domain.bo.KbDatasetBo;
import org.zhiku.ai.knowledge.domain.vo.KbDatasetVo;
import org.zhiku.ai.knowledge.domain.vo.RetrievalHit;
import org.zhiku.ai.knowledge.domain.vo.RetrievalQuery;
import org.zhiku.ai.knowledge.mapper.KbChunkMapper;
import org.zhiku.ai.knowledge.mapper.KbDatasetMapper;
import org.zhiku.ai.knowledge.service.IAiRetrievalService;
import org.zhiku.ai.knowledge.service.IKbDatasetService;
import org.zhiku.ai.knowledge.service.IKbDocumentService;
import org.zhiku.ai.knowledge.vector.KbVectorStore;
import org.zhiku.common.core.domain.PageResult;
import org.zhiku.common.core.domain.R;
import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.core.validate.AddGroup;
import org.zhiku.common.core.validate.EditGroup;
import org.zhiku.common.log.annotation.Log;
import org.zhiku.common.log.enums.BusinessType;
import org.zhiku.common.mybatis.core.page.PageQuery;
import org.zhiku.common.redis.annotation.RateLimiter;
import org.zhiku.common.redis.annotation.RepeatSubmit;
import org.zhiku.common.web.core.BaseController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库管理
 *
 * @author zhiku
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/kb/dataset")
public class KbDatasetController extends BaseController {

    private final IKbDatasetService datasetService;
    private final IKbDocumentService documentService;
    private final IAiRetrievalService retrievalService;
    private final KbDatasetMapper datasetMapper;
    private final KbChunkMapper chunkMapper;
    private final ObjectProvider<KbVectorStore> vectorStoreProvider;

    /**
     * 查询知识库列表
     */
    @SaCheckPermission("ai:kb:list")
    @GetMapping("/list")
    public R<PageResult<KbDatasetVo>> list(KbDatasetBo bo, PageQuery pageQuery) {
        return R.ok(datasetService.queryPageList(bo, pageQuery));
    }

    /**
     * 获取知识库详细信息
     */
    @SaCheckPermission("ai:kb:query")
    @GetMapping("/{datasetId}")
    public R<KbDatasetVo> getInfo(@NotNull @PathVariable Long datasetId) {
        return R.ok(datasetService.queryById(datasetId));
    }

    /**
     * 新增知识库
     */
    @SaCheckPermission("ai:kb:add")
    @Log(title = "知识库管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody KbDatasetBo bo) {
        return toAjax(datasetService.insertByBo(bo));
    }

    /**
     * 修改知识库
     */
    @SaCheckPermission("ai:kb:edit")
    @Log(title = "知识库管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody KbDatasetBo bo) {
        return toAjax(datasetService.updateByBo(bo));
    }

    /**
     * 删除知识库
     */
    @SaCheckPermission("ai:kb:remove")
    @Log(title = "知识库管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{datasetIds}")
    public R<Void> remove(@NotEmpty @PathVariable Long[] datasetIds) {
        return toAjax(datasetService.deleteWithValidByIds(List.of(datasetIds), true));
    }

    /**
     * 知识库统计（文档数/分块数/可用数）
     */
    @SaCheckPermission("ai:kb:query")
    @GetMapping("/{datasetId}/stat")
    public R<IKbDocumentService.DocumentStat> stat(@NotNull @PathVariable Long datasetId) {
        return R.ok(documentService.queryStat(datasetId));
    }

    /**
     * 检索命中测试（入库质量自检）
     */
    @SaCheckPermission("ai:kb:query")
    @Log(title = "知识库管理", businessType = BusinessType.OTHER)
    @RateLimiter(key = "kbHitTest", time = 60, count = 30)
    @PostMapping("/{datasetId}/hitTest")
    public R<List<RetrievalHit>> hitTest(@NotNull @PathVariable Long datasetId,
                                         @RequestBody HitTestRequest request) {
        RetrievalQuery query = new RetrievalQuery();
        query.setKbIds(List.of(datasetId));
        query.setText(request.query());
        query.setTopK(request.topK() == null ? 5 : request.topK());
        query.setRerankEnabled(Boolean.TRUE.equals(request.rerank()));
        return R.ok(retrievalService.search(query));
    }

    /**
     * 命中测试请求体
     */
    public record HitTestRequest(String query, Integer topK, Boolean rerank) {
    }

    /**
     * 向量对账（Milvus 计数 vs kb_chunk 计数，验收"零残留"用）
     */
    @SaCheckPermission("ai:kb:query")
    @GetMapping("/{datasetId}/vectorCount")
    public R<VectorCountResult> vectorCount(@NotNull @PathVariable Long datasetId) {
        KbDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null || dataset.getEmbedDim() == null) {
            throw new ServiceException("知识库不存在或未配置向量维度：" + datasetId);
        }
        KbVectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new ServiceException("向量服务不可用（zhiku.ai.milvus.enabled=false）");
        }
        LambdaQueryWrapper<KbChunk> lqw = new LambdaQueryWrapper<>();
        lqw.eq(KbChunk::getDatasetId, datasetId);
        Long mysqlCount = chunkMapper.selectCount(lqw);
        long milvusCount = vectorStore.count(dataset.getEmbedDim(), "kb_id == " + datasetId);
        return R.ok(new VectorCountResult(milvusCount, mysqlCount == null ? 0 : mysqlCount));
    }

    /**
     * 对账结果
     */
    public record VectorCountResult(long milvusCount, long mysqlChunkCount) {
    }

}
