package org.zhiku.ai.app.runner;

import org.junit.jupiter.api.Test;
import org.zhiku.common.core.exception.ServiceException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 对话类型注册表单测（mock 两个 handler 验证索引与未知类型）
 *
 * @author zhiku
 */
class ChatTypeRegistryTest {

    private ChatTypeHandler handler(String type) {
        ChatTypeHandler handler = mock(ChatTypeHandler.class);
        when(handler.type()).thenReturn(type);
        return handler;
    }

    @Test
    void indexesHandlersByType() {
        ChatTypeHandler qa = handler("knowledge_qa");
        ChatTypeHandler agent = handler("agent");
        ChatTypeRegistry registry = new ChatTypeRegistry(List.of(qa, agent));
        assertEquals(qa, registry.get("knowledge_qa"));
        assertEquals(agent, registry.get("agent"));
        assertEquals(2, registry.listTypes().size());
    }

    @Test
    void unknownTypeRejected() {
        ChatTypeRegistry registry = new ChatTypeRegistry(List.of(handler("knowledge_qa")));
        assertThrows(ServiceException.class, () -> registry.get("agent"));
        assertThrows(ServiceException.class, () -> registry.get(null));
    }
}
