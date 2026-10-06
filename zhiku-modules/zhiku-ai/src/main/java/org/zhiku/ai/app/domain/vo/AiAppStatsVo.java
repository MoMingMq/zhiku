package org.zhiku.ai.app.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 应用监控统计（概览页监控块）
 *
 * @author zhiku
 */
@Data
public class AiAppStatsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 提问总数（role=1 消息数）
     */
    private Long totalQuestions;

    /**
     * 会话总数（含公开匿名）
     */
    private Long totalConversations;

    /**
     * token 总消耗（prompt+completion）
     */
    private Long totalTokens;

    /**
     * 今日提问数
     */
    private Long todayQuestions;

    /**
     * 近 7 日（日期/提问数/消耗 token）
     */
    private List<DailyStat> last7d;

    /**
     * 单日统计
     */
    @Data
    public static class DailyStat implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String date;
        private Long questions;
        private Long tokens;
    }
}
