package org.zhiku.ai.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.zhiku.ai.knowledge.domain.vo.KbProblemChunkVo;
import org.zhiku.ai.knowledge.domain.vo.KbProblemVo;
import org.zhiku.ai.knowledge.service.IKbProblemService;
import org.zhiku.common.core.domain.R;
import org.zhiku.common.log.annotation.Log;
import org.zhiku.common.log.enums.BusinessType;
import org.zhiku.common.redis.annotation.RepeatSubmit;
import org.zhiku.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库问题管理（问题绑定分段，问题向量参与检索）
 *
 * @author zhiku
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/kb/problem")
public class KbProblemController extends BaseController {

    private final IKbProblemService problemService;

    /**
     * 查询问题列表
     */
    @SaCheckPermission("ai:kb:query")
    @GetMapping("/list")
    public R<List<KbProblemVo>> list(@RequestParam Long datasetId, @RequestParam(required = false) String keyword) {
        return R.ok(problemService.queryList(datasetId, keyword));
    }

    /**
     * 查询问题详情（含关联分段ID）
     */
    @SaCheckPermission("ai:kb:query")
    @GetMapping("/{problemId}")
    public R<KbProblemVo> getInfo(@NotNull @PathVariable Long problemId) {
        return R.ok(problemService.queryById(problemId));
    }

    /**
     * 查询问题关联的分段（含文档名）
     */
    @SaCheckPermission("ai:kb:query")
    @GetMapping("/{problemId}/chunks")
    public R<List<KbProblemChunkVo>> boundChunks(@NotNull @PathVariable Long problemId) {
        return R.ok(problemService.queryBoundChunks(problemId));
    }

    /**
     * 批量新增问题（content 一行一个问题，逐条向量化）
     */
    @SaCheckPermission("ai:kb:add")
    @Log(title = "知识库管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Integer> add(@RequestBody ProblemBody body) {
        return R.ok(problemService.insertBatch(body.datasetId(), body.content()).size());
    }

    /**
     * 绑定分段（追加关联）
     */
    @SaCheckPermission("ai:kb:edit")
    @Log(title = "知识库管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{problemId}/bind")
    public R<Void> bind(@NotNull @PathVariable Long problemId, @RequestBody BindBody body) {
        return toAjax(problemService.bindChunks(problemId, body.chunkIds()));
    }

    /**
     * 解绑分段
     */
    @SaCheckPermission("ai:kb:edit")
    @Log(title = "知识库管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{problemId}/unbind")
    public R<Void> unbind(@NotNull @PathVariable Long problemId, @RequestBody BindBody body) {
        return toAjax(problemService.unbindChunks(problemId, body.chunkIds()));
    }

    /**
     * 删除问题（同步删除向量与关联）
     */
    @SaCheckPermission("ai:kb:remove")
    @Log(title = "知识库管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{problemIds}")
    public R<Void> remove(@NotEmpty @PathVariable Long[] problemIds) {
        return toAjax(problemService.deleteByIds(List.of(problemIds)));
    }

    /**
     * 问题创建请求体（content 按行拆分为多个问题）
     */
    public record ProblemBody(Long datasetId, String content) {
    }

    /**
     * 绑定/解绑请求体
     */
    public record BindBody(List<Long> chunkIds) {
    }

}
