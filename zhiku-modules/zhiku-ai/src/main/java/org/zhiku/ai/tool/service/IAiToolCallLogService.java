package org.zhiku.ai.tool.service;

import org.zhiku.ai.tool.domain.AiToolCallLog;
import org.zhiku.ai.tool.domain.vo.AiToolCallLogVo;
import org.zhiku.common.core.domain.PageResult;
import org.zhiku.common.mybatis.core.page.PageQuery;

/**
 * 工具调用审计 Service 接口
 *
 * @author zhiku
 */
public interface IAiToolCallLogService {

    /**
     * 记录一次工具调用（成败均记；执行器 finally 调用）
     */
    void insertLog(AiToolCallLog logRow);

    /**
     * 审计分页查询（toolId 可选过滤）
     */
    PageResult<AiToolCallLogVo> queryPageList(Long toolId, PageQuery pageQuery);
}
