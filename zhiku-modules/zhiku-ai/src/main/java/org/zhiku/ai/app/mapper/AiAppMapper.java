package org.zhiku.ai.app.mapper;

import org.apache.ibatis.annotations.Param;
import org.zhiku.ai.app.domain.AiApp;
import org.zhiku.ai.app.domain.vo.AiAppVo;
import org.zhiku.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.List;
import java.util.Map;

/**
 * 应用管理 Mapper 接口（第二泛型 Vo 由 BaseMapperPlus 自动转换；统计 SQL 见 AiAppMapper.xml）
 *
 * @author zhiku
 */
public interface AiAppMapper extends BaseMapperPlus<AiApp, AiAppVo> {

    /**
     * 应用统计总量（提问/会话/token/今日）
     */
    Map<String, Object> selectAppStatsTotals(@Param("appId") Long appId);

    /**
     * 近 7 日按日统计
     */
    List<Map<String, Object>> selectAppStatsDaily(@Param("appId") Long appId);
}
