package org.zhiku.ai.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.zhiku.ai.knowledge.domain.vo.KbChunkVo;
import org.zhiku.ai.knowledge.domain.vo.KbDocumentVo;
import org.zhiku.ai.knowledge.domain.vo.KbProblemVo;
import org.zhiku.ai.knowledge.service.IKbChunkService;
import org.zhiku.ai.knowledge.service.IKbDocumentService;
import org.zhiku.ai.knowledge.service.IKbProblemService;
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
import org.springframework.web.bind.annotation.RequestBody;
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
    private final IKbProblemService problemService;

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
     * 查询文档的分段列表
     */
    @SaCheckPermission("ai:kb:query")
    @GetMapping("/{documentId}/chunks")
    public R<List<KbChunkVo>> chunks(@NotNull @PathVariable Long documentId) {
        return R.ok(chunkService.queryByDocumentId(documentId));
    }

    /**
     * 分页查询文档分段（绑定弹窗滚动分页：searchType=title|content）
     */
    @SaCheckPermission("ai:kb:query")
    @GetMapping("/{documentId}/chunk/page")
    public R<PageResult<KbChunkVo>> chunkPage(@NotNull @PathVariable Long documentId,
                                              @RequestParam(required = false) String searchType,
                                              @RequestParam(required = false) String keyword,
                                              PageQuery pageQuery) {
        return R.ok(chunkService.queryPage(documentId, searchType, keyword, pageQuery));
    }

    /**
     * 切换分段禁用状态（禁用后不参与检索）
     */
    @SaCheckPermission("ai:kb:edit")
    @Log(title = "知识库管理", businessType = BusinessType.UPDATE)
    @PutMapping("/chunk/{chunkId}/disabled")
    public R<Void> changeChunkDisabled(@NotNull @PathVariable Long chunkId, @RequestParam boolean disabled) {
        return toAjax(chunkService.changeDisabled(chunkId, disabled));
    }

    /**
     * 查询分段已绑定的问题（分段编辑弹窗右侧面板；绑定/解绑复用问题侧接口）
     */
    @SaCheckPermission("ai:kb:query")
    @GetMapping("/chunk/{chunkId}/problems")
    public R<List<KbProblemVo>> chunkProblems(@NotNull @PathVariable Long chunkId) {
        return R.ok(problemService.queryChunkProblems(chunkId));
    }

    /**
     * 编辑分段（标题/正文；正文变更自动重新向量化）
     */
    @SaCheckPermission("ai:kb:edit")
    @Log(title = "知识库管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/chunk/{chunkId}")
    public R<Void> editChunk(@NotNull @PathVariable Long chunkId, @RequestBody ChunkEditBody body) {
        return toAjax(chunkService.editChunk(chunkId, body.title(), body.content()));
    }

    /**
     * 编辑分段请求体
     */
    public record ChunkEditBody(String title, String content) {
    }

    /**
     * 新增分段（追加到文档末尾并向量化）
     */
    @SaCheckPermission("ai:kb:edit")
    @Log(title = "知识库管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/{documentId}/chunk")
    public R<Long> addChunk(@NotNull @PathVariable Long documentId, @RequestBody ChunkEditBody body) {
        return R.ok(chunkService.addChunk(documentId, body.title(), body.content()));
    }

    /**
     * 删除分段（同步删除向量）
     */
    @SaCheckPermission("ai:kb:edit")
    @Log(title = "知识库管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/chunk/{chunkId}")
    public R<Void> deleteChunk(@NotNull @PathVariable Long chunkId) {
        return toAjax(chunkService.deleteChunk(chunkId));
    }

    /**
     * 分段排序（拖拽后按新顺序重排 seq）
     */
    @SaCheckPermission("ai:kb:edit")
    @Log(title = "知识库管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{documentId}/chunks/order")
    public R<Void> reorderChunks(@NotNull @PathVariable Long documentId, @RequestBody List<Long> chunkIds) {
        return toAjax(chunkService.reorderChunks(documentId, chunkIds));
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
