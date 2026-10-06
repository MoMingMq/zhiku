package org.zhiku.ai.app.runner;

import org.zhiku.ai.app.domain.vo.AppConfigField;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 对话类型策略：配置定义 + 校验 + 执行 内聚（新对话类型=新增实现类）
 * <p>注册表按 {@link #type()} 索引；前端设置页按 configFields 动态渲染。
 *
 * @author zhiku
 */
public interface ChatTypeHandler {

    /**
     * 类型码（如 knowledge_qa）
     */
    String type();

    /**
     * 显示名（如 知识库问答）
     */
    String typeName();

    /**
     * 专属配置字段定义（前端动态渲染）
     */
    List<AppConfigField> configFields();

    /**
     * 默认配置 JSON
     */
    String defaultConfig();

    /**
     * 保存时校验 config JSON（结构+取值）
     */
    void validateConfig(String configJson);

    /**
     * 执行对话（SSE 流式；全链路：落库→检索→组装→生成→帧→落库）
     */
    SseEmitter chat(AppChatCommand command);

}
