package org.zhiku.ai.model.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.zhiku.ai.model.domain.bo.AiModelBo;
import org.zhiku.ai.model.domain.vo.AiModelVo;
import org.zhiku.ai.model.domain.vo.ModelTestResult;
import org.zhiku.ai.model.service.IAiModelService;
import org.zhiku.common.core.domain.PageResult;
import org.zhiku.common.core.domain.R;
import org.zhiku.common.core.validate.AddGroup;
import org.zhiku.common.core.validate.EditGroup;
import org.zhiku.common.log.annotation.Log;
import org.zhiku.common.log.enums.BusinessType;
import org.zhiku.common.mybatis.core.page.PageQuery;
import org.zhiku.common.redis.annotation.RateLimiter;
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
import java.util.Map;

/**
 * AI 模型配置
 *
 * @author zhiku
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/model")
public class AiModelController extends BaseController {

    private final IAiModelService modelService;

    /**
     * 查询模型配置列表
     */
    @SaCheckPermission("ai:model:list")
    @GetMapping("/list")
    public R<PageResult<AiModelVo>> list(AiModelBo bo, PageQuery pageQuery) {
        return R.ok(modelService.queryPageList(bo, pageQuery));
    }

    /**
     * 获取模型配置详细信息
     */
    @SaCheckPermission("ai:model:query")
    @GetMapping("/{modelId}")
    public R<AiModelVo> getInfo(@NotNull @PathVariable Long modelId) {
        return R.ok(modelService.queryById(modelId));
    }

    /**
     * 新增模型配置
     */
    @SaCheckPermission("ai:model:add")
    @Log(title = "模型管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody AiModelBo bo) {
        return toAjax(modelService.insertByBo(bo));
    }

    /**
     * 修改模型配置
     */
    @SaCheckPermission("ai:model:edit")
    @Log(title = "模型管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody AiModelBo bo) {
        return toAjax(modelService.updateByBo(bo));
    }

    /**
     * 删除模型配置
     */
    @SaCheckPermission("ai:model:remove")
    @Log(title = "模型管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{modelIds}")
    public R<Void> remove(@NotEmpty @PathVariable Long[] modelIds) {
        return toAjax(modelService.deleteWithValidByIds(List.of(modelIds), true));
    }

    /**
     * 连通性测试（按模型类型发最小请求，防刷限流）
     */
    @SaCheckPermission("ai:model:query")
    @Log(title = "模型管理", businessType = BusinessType.OTHER)
    @RateLimiter(key = "aiModelTest", time = 60, count = 10)
    @PostMapping("/test/{modelId}")
    public R<ModelTestResult> test(@NotNull @PathVariable Long modelId) {
        return R.ok(modelService.testConnection(modelId));
    }

    /**
     * 模型参数设置（Map 承载：参数名→默认值；应用侧按值类型动态生成调整控件）
     */
    @SaCheckPermission("ai:model:edit")
    @Log(title = "模型管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{modelId}/params")
    public R<Void> updateParams(@NotNull @PathVariable Long modelId, @RequestBody Map<String, Object> params) {
        return toAjax(modelService.updateModelParams(modelId, params));
    }

}
