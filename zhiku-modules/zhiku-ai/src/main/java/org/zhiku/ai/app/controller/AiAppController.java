package org.zhiku.ai.app.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.zhiku.ai.app.domain.bo.AiAppBo;
import org.zhiku.ai.app.domain.vo.AiAppStatsVo;
import org.zhiku.ai.app.domain.vo.AiAppVo;
import org.zhiku.ai.app.domain.vo.ChatTypeVo;
import org.zhiku.ai.app.service.IAiAppService;
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
 * 应用管理
 *
 * @author zhiku
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/app")
public class AiAppController extends BaseController {

    private final IAiAppService appService;

    /**
     * 应用列表（分页）
     */
    @SaCheckPermission("ai:app:list")
    @GetMapping("/list")
    public R<PageResult<AiAppVo>> list(AiAppBo bo, PageQuery pageQuery) {
        return R.ok(appService.queryPageList(bo, pageQuery));
    }

    /**
     * 应用详情
     */
    @SaCheckPermission("ai:app:query")
    @GetMapping("/{appId}")
    public R<AiAppVo> getInfo(@NotNull @PathVariable Long appId) {
        return R.ok(appService.queryById(appId));
    }

    /**
     * 对话类型清单（设置页动态配置用）
     */
    @SaCheckPermission("ai:app:list")
    @GetMapping("/chat-types")
    public R<List<ChatTypeVo>> chatTypes() {
        return R.ok(appService.listChatTypes());
    }

    /**
     * 新增应用（新建对话框：名称/描述/应用类型）
     */
    @SaCheckPermission("ai:app:add")
    @Log(title = "应用管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Long> add(@Validated(AddGroup.class) @RequestBody AiAppBo bo) {
        appService.insertByBo(bo);
        return R.ok(bo.getAppId());
    }

    /**
     * 修改应用（设置页全量保存）
     */
    @SaCheckPermission("ai:app:edit")
    @Log(title = "应用管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody AiAppBo bo) {
        return toAjax(appService.updateByBo(bo));
    }

    /**
     * 删除应用（级联知识库/工具关系与会话）
     */
    @SaCheckPermission("ai:app:remove")
    @Log(title = "应用管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{appIds}")
    public R<Void> remove(@NotEmpty @PathVariable Long[] appIds) {
        return toAjax(appService.deleteByIds(List.of(appIds)));
    }

    /**
     * 公开访问开关（返回公开地址码；关闭传 false）
     */
    @SaCheckPermission("ai:app:edit")
    @Log(title = "应用管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{appId}/share/{enabled}")
    public R<java.util.Map<String, String>> toggleShare(@NotNull @PathVariable Long appId, @NotNull @PathVariable Boolean enabled) {
        // 注意：R.ok(String) 会命中 msg 重载，字符串数据必须用 Map 包装返回
        String code = appService.toggleShare(appId, enabled);
        return R.ok(java.util.Map.of("shareCode", code == null ? "" : code));
    }

    /**
     * 更新 IP 白名单
     */
    @SaCheckPermission("ai:app:edit")
    @Log(title = "应用管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{appId}/ip-whitelist")
    public R<Void> updateIpWhitelist(@NotNull @PathVariable Long appId, @RequestBody IpWhitelistBody body) {
        return toAjax(appService.updateIpWhitelist(appId, body.ipWhitelist()));
    }

    /**
     * 监控统计
     */
    @SaCheckPermission("ai:app:query")
    @GetMapping("/{appId}/stats")
    public R<AiAppStatsVo> stats(@NotNull @PathVariable Long appId) {
        return R.ok(appService.stats(appId));
    }

    /**
     * IP 白名单请求体
     */
    public record IpWhitelistBody(String ipWhitelist) {
    }

}
