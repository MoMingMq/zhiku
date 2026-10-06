package org.zhiku.ai.app.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 对话类型清单条目（GET /ai/app/chat-types 返回）
 *
 * @author zhiku
 */
@Data
public class ChatTypeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 类型码（如 knowledge_qa）
     */
    private String type;

    /**
     * 显示名（如 知识库问答）
     */
    private String typeName;

    /**
     * 专属配置字段定义
     */
    private List<AppConfigField> configFields;

    /**
     * 默认配置 JSON
     */
    private String defaultConfig;

}
