package org.zhiku.ai.tool.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.zhiku.ai.tool.domain.bo.AiToolBo;
import org.zhiku.ai.tool.domain.vo.AiToolCallLogVo;
import org.zhiku.ai.tool.domain.vo.AiToolVo;
import org.zhiku.ai.tool.execute.ToolExecResult;
import org.zhiku.ai.tool.service.IAiToolCallLogService;
import org.zhiku.ai.tool.service.IAiToolService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 工具管理（HTTP 模板工具，供模型 Function Calling 调用）
 *
 * @author zhiku
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/tool")
public class AiToolController extends BaseController {

    private final IAiToolService toolService;

    private final IAiToolCallLogService logService;

    /**
     * 查询工具列表（分页）
     */
    @SaCheckPermission("ai:tool:list")
    @GetMapping("/list")
    public R<PageResult<AiToolVo>> list(AiToolBo bo, PageQuery pageQuery) {
        return R.ok(toolService.queryPageList(bo, pageQuery));
    }

    /**
     * 查询工具详情（凭据掩码）
     */
    @SaCheckPermission("ai:tool:query")
    @GetMapping("/{toolId}")
    public R<AiToolVo> getInfo(@NotNull @PathVariable Long toolId) {
        return R.ok(toolService.queryById(toolId));
    }

    /**
     * 新增工具
     */
    @SaCheckPermission("ai:tool:add")
    @Log(title = "工具管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody AiToolBo bo) {
        return toAjax(toolService.insertByBo(bo));
    }

    /**
     * 修改工具（鉴权凭据留空=不修改）
     */
    @SaCheckPermission("ai:tool:edit")
    @Log(title = "工具管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody AiToolBo bo) {
        return toAjax(toolService.updateByBo(bo));
    }

    /**
     * 删除工具（级联删除挂载关系）
     */
    @SaCheckPermission("ai:tool:remove")
    @Log(title = "工具管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{toolIds}")
    public R<Void> remove(@NotEmpty @PathVariable Long[] toolIds) {
        return toAjax(toolService.deleteByIds(List.of(toolIds)));
    }

    /**
     * 试运行（示例参数真实调用，与对话调用共用执行器）
     */
    @SaCheckPermission("ai:tool:test")
    @Log(title = "工具管理", businessType = BusinessType.OTHER)
    @RepeatSubmit()
    @PostMapping("/test/{toolId}")
    public R<ToolExecResult> test(@NotNull @PathVariable Long toolId, @RequestBody Map<String, Object> params) {
        return R.ok(toolService.testRun(toolId, params));
    }

    /**
     * 应用挂载清单（M4 应用配置页）
     */
    @SaCheckPermission("ai:tool:query")
    @GetMapping("/app/{appId}")
    public R<List<AiToolVo>> appTools(@NotNull @PathVariable Long appId) {
        return R.ok(toolService.listToolsByApp(appId));
    }

    /**
     * 绑定工具到应用
     */
    @SaCheckPermission("ai:tool:edit")
    @Log(title = "工具管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/app/{appId}/bind")
    public R<Void> bind(@NotNull @PathVariable Long appId, @RequestBody BindBody body) {
        return toAjax(toolService.bindTools(appId, body.toolIds()));
    }

    /**
     * 解绑工具
     */
    @SaCheckPermission("ai:tool:edit")
    @Log(title = "工具管理", businessType = BusinessType.UPDATE)
    @DeleteMapping("/app/{appId}/unbind")
    public R<Void> unbind(@NotNull @PathVariable Long appId, @RequestBody BindBody body) {
        return toAjax(toolService.unbindTools(appId, body.toolIds()));
    }

    /**
     * 调用记录（审计分页，toolId 可选过滤）
     */
    @SaCheckPermission("ai:tool:log")
    @GetMapping("/log")
    public R<PageResult<AiToolCallLogVo>> logPage(@RequestParam(required = false) Long toolId, PageQuery pageQuery) {
        return R.ok(logService.queryPageList(toolId, pageQuery));
    }

    /**
     * 绑定/解绑请求体
     */
    public record BindBody(List<Long> toolIds) {
    }

}
