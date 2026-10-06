package org.zhiku.ai.app.service;

import org.zhiku.ai.app.domain.bo.AiAppBo;
import org.zhiku.ai.app.domain.vo.AiAppStatsVo;
import org.zhiku.ai.app.domain.vo.AiAppVo;
import org.zhiku.ai.app.domain.vo.ChatTypeVo;
import org.zhiku.common.core.domain.PageResult;
import org.zhiku.common.mybatis.core.page.PageQuery;

import java.util.List;

/**
 * 应用管理 Service 接口
 *
 * @author zhiku
 */
public interface IAiAppService {

    /**
     * 分页查询（组装 kbIds/toolIds/modelName）
     */
    PageResult<AiAppVo> queryPageList(AiAppBo bo, PageQuery pageQuery);

    /**
     * 详情（含 kbIds/toolIds/modelName）
     */
    AiAppVo queryById(Long appId);

    /**
     * 新增（app_type 仅 simple；chatType 经注册表校验；模型存在性校验）
     */
    Boolean insertByBo(AiAppBo bo);

    /**
     * 修改（config 变更时 kb_rel diff 同步）
     */
    Boolean updateByBo(AiAppBo bo);

    /**
     * 批量删除（级联 kb_rel/tool_rel/会话/消息）
     */
    Boolean deleteByIds(List<Long> appIds);

    /**
     * 对话类型清单
     */
    List<ChatTypeVo> listChatTypes();

    /**
     * 公开访问开关（开=生成新 shareCode；关=失效，复开换新码旧链接立即失效）
     */
    String toggleShare(Long appId, boolean enabled);

    /**
     * 更新 IP 白名单（格式校验：单 IP/CIDR 逗号分隔）
     */
    Boolean updateIpWhitelist(Long appId, String ipWhitelist);

    /**
     * 监控统计（提问/会话/token 总量/今日提问 + 近 7 日）
     */
    AiAppStatsVo stats(Long appId);

    /**
     * 按公开码查启用公开的应用（公开端点用；不存在/未开启统一返回 null 由调用方 404）
     */
    AiAppVo findByShareCode(String shareCode);
}
