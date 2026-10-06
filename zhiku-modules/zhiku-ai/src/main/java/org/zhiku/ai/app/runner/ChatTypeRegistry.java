package org.zhiku.ai.app.runner;

import org.zhiku.ai.app.domain.vo.ChatTypeVo;
import org.zhiku.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 对话类型注册表（Spring 注入全部实现按 type 索引，对齐 M2 ChunkingStrategy 注册式先例）
 *
 * @author zhiku
 */
@Component
public class ChatTypeRegistry {

    private final Map<String, ChatTypeHandler> handlers;

    public ChatTypeRegistry(List<ChatTypeHandler> handlerList) {
        this.handlers = handlerList.stream()
            .collect(Collectors.toMap(ChatTypeHandler::type, Function.identity()));
    }

    /**
     * 按类型码取实现
     */
    public ChatTypeHandler get(String type) {
        ChatTypeHandler handler = type == null ? null : handlers.get(type);
        if (handler == null) {
            throw new ServiceException("未知对话类型：" + type);
        }
        return handler;
    }

    /**
     * 类型清单（GET /ai/app/chat-types）
     */
    public List<ChatTypeVo> listTypes() {
        return handlers.values().stream().map(h -> {
            ChatTypeVo vo = new ChatTypeVo();
            vo.setType(h.type());
            vo.setTypeName(h.typeName());
            vo.setConfigFields(h.configFields());
            vo.setDefaultConfig(h.defaultConfig());
            return vo;
        }).collect(Collectors.toList());
    }

}
