package org.zhiku.ai.tool.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.zhiku.ai.tool.domain.AiToolCallLog;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 工具调用审计视图对象 ai_tool_call_log
 *
 * @author zhiku
 */
@Data
@AutoMapper(target = AiToolCallLog.class)
public class AiToolCallLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日志ID
     */
    private Long logId;

    /**
     * 工具ID
     */
    private Long toolId;

    /**
     * 工具名（冗余）
     */
    private String toolName;

    /**
     * 应用ID（试运行为空）
     */
    private Long appId;

    /**
     * 消息ID（对话场景）
     */
    private Long messageId;

    /**
     * 场景（0试运行 1对话调用）
     */
    private String scene;

    /**
     * 渲染后请求快照（鉴权头已脱敏）
     */
    private String requestSnapshot;

    /**
     * 响应快照（截断后）
     */
    private String responseSnapshot;

    /**
     * HTTP 状态码（异常为空）
     */
    private Integer httpStatus;

    /**
     * 是否成功（0否 1是）
     */
    private String success;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 耗时毫秒
     */
    private Integer durationMs;

    /**
     * 创建时间
     */
    private Date createTime;

}
