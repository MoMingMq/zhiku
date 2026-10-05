package org.zhiku.ai.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.zhiku.ai.knowledge.domain.bo.KbDatasetBo;
import org.zhiku.ai.knowledge.domain.vo.KbDatasetVo;
import org.zhiku.ai.knowledge.service.IKbDatasetService;
import org.zhiku.ai.knowledge.service.IKbDocumentService;
import org.zhiku.common.core.domain.PageResult;
import org.zhiku.common.core.domain.R;
import org.zhiku.common.core.validate.AddGroup;
import org.zhiku.common.core.validate.EditGroup;
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

}
