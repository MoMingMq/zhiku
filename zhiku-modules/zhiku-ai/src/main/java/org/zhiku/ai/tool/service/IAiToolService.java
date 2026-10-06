package org.zhiku.ai.tool.service;

import org.zhiku.ai.tool.domain.bo.AiToolBo;
import org.zhiku.ai.tool.domain.vo.AiToolVo;
import org.zhiku.ai.tool.execute.ToolExecResult;
import org.zhiku.common.core.domain.PageResult;
import org.zhiku.common.mybatis.core.page.PageQuery;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

/**
 * 工具管理 Service 接口
 * <p>M4 契约：{@code listCallbacksByApp / bindTools / unbindTools / listToolsByApp}
 *
 * @author zhiku
 */
public interface IAiToolService {

    /**
     * 分页查询（名称模糊/状态过滤，凭据掩码）
     */
    PageResult<AiToolVo> queryPageList(AiToolBo bo, PageQuery pageQuery);

    /**
     * 详情（凭据掩码，永不回显明文）
     */
    AiToolVo queryById(Long toolId);

    /**
     * 新增（工具名唯一/Schema 根 object/URL host 禁占位等校验）
     */
    Boolean insertByBo(AiToolBo bo);

    /**
     * 修改（鉴权凭据留空=不修改）
     */
    Boolean updateByBo(AiToolBo bo);

    /**
     * 批量删除（级联删除应用挂载关系；审计表冗余名保留历史）
     */
    Boolean deleteByIds(List<Long> toolIds);

    /**
     * 试运行（与对话调用共用 ToolExecutor，scene=0）
     */
    ToolExecResult testRun(Long toolId, Map<String, Object> params);

    /**
     * 应用挂载清单（M4 应用配置页）
     */
    List<AiToolVo> listToolsByApp(Long appId);

    /**
     * 绑定工具到应用（幂等：已有关系跳过）
     */
    Boolean bindTools(Long appId, List<Long> toolIds);

    /**
     * 解绑
     */
    Boolean unbindTools(Long appId, List<Long> toolIds);

    /**
     * M4 契约：按应用组装 Spring AI 工具回调（仅启用态工具）
     */
    List<ToolCallback> listCallbacksByApp(Long appId);
}
