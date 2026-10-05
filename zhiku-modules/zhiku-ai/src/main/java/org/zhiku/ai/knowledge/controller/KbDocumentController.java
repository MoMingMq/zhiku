package org.zhiku.ai.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.zhiku.ai.knowledge.domain.vo.KbChunkVo;
import org.zhiku.ai.knowledge.domain.vo.KbDocumentVo;
import org.zhiku.ai.knowledge.service.IKbChunkService;
import org.zhiku.ai.knowledge.service.IKbDocumentService;
import org.zhiku.common.core.domain.PageResult;
import org.zhiku.common.core.domain.R;
import org.zhiku.common.log.annotation.Log;
import org.zhiku.common.log.enums.BusinessType;
import org.zhiku.common.mybatis.core.page.PageQuery;
import org.zhiku.common.redis.annotation.RepeatSubmit;
import org.zhiku.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库文档与分块（上传/重试/重切由 Task 12 入库管线接入）
 *
 * @author zhiku
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/kb/document")
public class KbDocumentController extends BaseController {

    private final IKbDocumentService documentService;
    private final IKbChunkService chunkService;

    /**
     * 查询文档列表（左树，按知识库过滤）
     */
    @SaCheckPermission("ai:kb:list")
    @GetMapping("/list")
    public R<PageResult<KbDocumentVo>> list(@RequestParam Long datasetId,
                                            @RequestParam(required = false) String fileName,
                                            @RequestParam(required = false) String status,
                                            PageQuery pageQuery) {
        return R.ok(documentService.queryPageList(datasetId, fileName, status, pageQuery));
    }

    /**
     * 获取文档详细信息
     */
    @SaCheckPermission("ai:kb:query")
    @GetMapping("/{documentId}")
    public R<KbDocumentVo> getInfo(@NotNull @PathVariable Long documentId) {
        return R.ok(documentService.queryById(documentId));
    }

    /**
     * 上传文档（存储后异步入库：解析→分块→向量化）
     */
    @SaCheckPermission("ai:kb:add")
    @Log(title = "知识库管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/upload")
    public R<Long> upload(@RequestParam Long datasetId, @RequestParam("file") MultipartFile file) {
        return R.ok(documentService.upload(datasetId, file));
    }

    /**
     * 重试失败文档
     */
    @SaCheckPermission("ai:kb:edit")
    @Log(title = "知识库管理", businessType = BusinessType.OTHER)
    @RepeatSubmit()
    @PostMapping("/{documentId}/retry")
    public R<Void> retry(@NotNull @PathVariable Long documentId) {
        return toAjax(documentService.retry(documentId));
    }

    /**
     * 重切文档（换分块策略后整文档重跑）
     */
    @SaCheckPermission("ai:kb:edit")
    @Log(title = "知识库管理", businessType = BusinessType.OTHER)
    @RepeatSubmit()
    @PostMapping("/{documentId}/reingest")
    public R<Void> reingest(@NotNull @PathVariable Long documentId) {
        return toAjax(documentService.reingest(documentId));
    }

    /**
     * 查询文档的分块列表（右表）
     */
    @SaCheckPermission("ai:kb:query")
    @GetMapping("/{documentId}/chunks")
    public R<List<KbChunkVo>> chunks(@NotNull @PathVariable Long documentId) {
        return R.ok(chunkService.queryByDocumentId(documentId));
    }

    /**
     * 切换分块禁用状态（禁用后不参与检索）
     */
    @SaCheckPermission("ai:kb:edit")
    @Log(title = "知识库管理", businessType = BusinessType.UPDATE)
    @PutMapping("/chunk/{chunkId}/disabled")
    public R<Void> changeChunkDisabled(@NotNull @PathVariable Long chunkId, @RequestParam boolean disabled) {
        return toAjax(chunkService.changeDisabled(chunkId, disabled));
    }

    /**
     * 删除文档
     */
    @SaCheckPermission("ai:kb:remove")
    @Log(title = "知识库管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{documentId}")
    public R<Void> remove(@NotNull @PathVariable Long documentId) {
        return toAjax(documentService.deleteById(documentId));
    }

}
