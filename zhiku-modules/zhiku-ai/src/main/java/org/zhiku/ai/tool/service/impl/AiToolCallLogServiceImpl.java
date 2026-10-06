package org.zhiku.ai.tool.service.impl;

import lombok.RequiredArgsConstructor;
import org.zhiku.ai.tool.domain.AiToolCallLog;
import org.zhiku.ai.tool.domain.vo.AiToolCallLogVo;
import org.zhiku.ai.tool.mapper.AiToolCallLogMapper;
import org.zhiku.ai.tool.service.IAiToolCallLogService;
import org.zhiku.common.core.domain.PageResult;
import org.zhiku.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;

/**
 * 工具调用审计 Service 实现
 *
 * @author zhiku
 */
@RequiredArgsConstructor
@Service
public class AiToolCallLogServiceImpl implements IAiToolCallLogService {

    private final AiToolCallLogMapper baseMapper;

    @Override
    public void insertLog(AiToolCallLog logRow) {
        baseMapper.insert(logRow);
    }

    @Override
    public PageResult<AiToolCallLogVo> queryPageList(Long toolId, PageQuery pageQuery) {
        var page = baseMapper.selectVoPage(pageQuery.build(),
            baseMapper.lambda().eqIfPresent(AiToolCallLog::getToolId, toolId)
                .orderByDesc(AiToolCallLog::getLogId));
        return PageResult.build(page.getRecords(), page.getTotal());
    }
}
