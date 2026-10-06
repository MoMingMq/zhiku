package org.zhiku.ai.app.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工具调用记录（SSE tool_call/tool_result 帧 / 消息落库共用结构）
 *
 * @author zhiku
 */
@Data
public class ToolCallRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 工具名
     */
    private String name;

    /**
     * 调用参数
     */
    private String args;

    /**
     * 执行结果（截断后）
     */
    private String result;

    /**
     * 是否成功
     */
    private Boolean success;

}
