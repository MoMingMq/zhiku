package org.zhiku.ai.tool.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 工具调用审计对象 ai_tool_call_log
 *
 * @author zhiku
 */
@Data
@TableName("ai_tool_call_log")
public class AiToolCallLog {

    /**
     * 日志ID
     */
    @TableId(value = "log_id")
    private Long logId;

    /**
     * 工具ID
     */
    private Long toolId;

    /**
     * 工具名（冗余，防工具删除后不可读）
     */
    private String toolName;

    /**
     * 应用ID（试运行为空）
     */
    private Long appId;

    /**
     * 消息ID（M4 对话时经 ToolContext 传入）
     */
    private Long messageId;

    /**
     * 场景（0试运行 1对话调用）
     */
    private String scene;

    /**
     * 渲染后请求快照（鉴权头脱敏）
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
     * 创建者
     */
    private Long createBy;

    /**
     * 创建时间
     */
    private Date createTime;

}
