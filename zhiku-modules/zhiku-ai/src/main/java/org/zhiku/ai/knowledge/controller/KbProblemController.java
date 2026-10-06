package org.zhiku.ai.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.zhiku.ai.knowledge.domain.vo.KbProblemVo;
import org.zhiku.ai.knowledge.service.IKbProblemService;
import org.zhiku.common.core.domain.R;
import org.zhiku.common.core.utils.StringUtils;
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
     * 查询问题详情（含关联分段）
     */
    @SaCheckPermission("ai:kb:query")
    @GetMapping("/{problemId}")
    public R<KbProblemVo> getInfo(@NotNull @PathVariable Long problemId) {
        return R.ok(problemService.queryById(problemId));
    }

    /**
     * 新增问题（文本向量化入库）
     */
    @SaCheckPermission("ai:kb:add")
    @Log(title = "知识库管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Long> add(@RequestBody ProblemBody body) {
        return R.ok(problemService.insertByBo(body.datasetId(), body.content(), body.chunkIds()));
    }

    /**
     * 编辑问题（文本变更重新向量化，重绑分段）
     */
    @SaCheckPermission("ai:kb:edit")
    @Log(title = "知识库管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@RequestBody ProblemBody body) {
        return toAjax(problemService.updateByBo(body.problemId(), body.content(), body.chunkIds()));
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
     * 问题请求体
     */
    public record ProblemBody(Long problemId, Long datasetId, String content, List<Long> chunkIds) {
    }

}
