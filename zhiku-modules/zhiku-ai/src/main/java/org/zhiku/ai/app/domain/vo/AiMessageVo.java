package org.zhiku.ai.app.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 对话消息视图对象 ai_message（citation/toolCalls 反序列化后返回）
 * <p>不走 @AutoMapper（List 字段与实体 JSON 字符串列类型不同），由服务层手动组装。
 *
 * @author zhiku
 */
@Data
public class AiMessageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 消息ID
     */
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
     * 引用溯源（反序列化，assistant 消息；与实体 String 列类型不同，忽略自动映射由服务层填充）
     */
    private List<CitationVo> citations;

    /**
     * 工具调用记录（反序列化：[{name,args,result}]；同上）
     */
    private List<ToolCallRecordVo> toolCalls;

    /**
     * 输入 token 用量
     */
    private Integer promptTokens;

    /**
     * 输出 token 用量
     */
    private Integer completionTokens;

    /**
     * 创建时间
     */
    private Date createTime;

}
