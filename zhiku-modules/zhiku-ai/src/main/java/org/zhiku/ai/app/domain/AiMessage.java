package org.zhiku.ai.app.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 对话消息对象 ai_message（含 citation 引用溯源与工具调用记录）
 *
 * @author zhiku
 */
@Data
@TableName("ai_message")
public class AiMessage {

    /**
     * 消息ID
     */
    @TableId(value = "message_id")
    private Long messageId;

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 会话内序号
     */
    private Integer seq;

    /**
     * 角色（1用户 2助手）
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 引用溯源 JSON [{chunkId,documentId,documentName,seq,headingPath,score,snippet}]
     */
    private String citation;

    /**
     * 工具调用记录 JSON [{name,args,result}]
     */
    private String toolCalls;

    /**
     * 输入 token 用量（计费预留）
     */
    private Integer promptTokens;

    /**
     * 输出 token 用量（计费预留）
     */
    private Integer completionTokens;

    /**
     * 创建者（公开会话为空）
     */
    private Long createBy;

    /**
     * 创建时间
     */
    private Date createTime;

}
