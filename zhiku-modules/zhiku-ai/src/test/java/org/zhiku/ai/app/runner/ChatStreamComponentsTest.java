package org.zhiku.ai.app.runner;

import org.junit.jupiter.api.Test;
import org.zhiku.ai.app.domain.vo.ToolCallRecordVo;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * 工具装饰器与流式帧提取器单测
 *
 * @author zhiku
 */
class ChatStreamComponentsTest {

    @Test
    void decoratorEmitsFramesAndCollects() {
        ChatFrameWriter writer = mock(ChatFrameWriter.class);
        ToolCallback inner = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn("weather_query");
        when(inner.getToolDefinition()).thenReturn(definition);
        when(inner.call(anyString())).thenReturn("{\"temp\":25}");

        List<ToolCallRecordVo> collector = new ArrayList<>();
        ToolCallback decorated = ToolCallStreamDecorator.decorate(inner, writer, collector);

        String result = decorated.call("{\"city\":\"北京\"}", null);

        assertEquals("{\"temp\":25}", result);
        var inOrder = inOrder(writer);
        inOrder.verify(writer).toolCall("weather_query", "{\"city\":\"北京\"}");
        inOrder.verify(writer).toolResult("weather_query", true, "{\"temp\":25}");
        assertEquals(1, collector.size());
        assertEquals("weather_query", collector.get(0).getName());
        assertEquals(Boolean.TRUE, collector.get(0).getSuccess());
    }

    @Test
    void decoratorCapturesToolException() {
        ChatFrameWriter writer = mock(ChatFrameWriter.class);
        ToolCallback inner = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn("bad_tool");
        when(inner.getToolDefinition()).thenReturn(definition);
        when(inner.call(anyString())).thenThrow(new RuntimeException("connection refused"));

        List<ToolCallRecordVo> collector = new ArrayList<>();
        String result = ToolCallStreamDecorator.decorate(inner, writer, collector).call("{}", null);

        assertTrue(result.contains("connection refused"));
        assertEquals(Boolean.FALSE, collector.get(0).getSuccess());
    }

    @Test
    void extractorAppendsTextAndCapturesUsage() {
        ChatStreamFrameExtractor extractor = new ChatStreamFrameExtractor();
        StringBuilder content = new StringBuilder();
        List<String> deltas = new ArrayList<>();

        Integer[] usage = extractor.handle(mockResponse("你好", 10, 5), content, deltas::add);

        assertEquals("你好", content.toString());
        assertEquals(1, deltas.size());
        assertEquals(10, usage[0]);
        assertEquals(5, usage[1]);
    }

    @Test
    void extractorSkipsNullText() {
        ChatStreamFrameExtractor extractor = new ChatStreamFrameExtractor();
        StringBuilder content = new StringBuilder();
        Integer[] usage = extractor.handle(mockResponse(null, null, null), content, s -> { });
        assertEquals(0, content.length());
        assertEquals(null, usage);
    }

    private ChatResponse mockResponse(String text, Integer promptTokens, Integer completionTokens) {
        ChatResponse resp = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage output = mock(AssistantMessage.class);
        when(resp.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(output);
        when(output.getText()).thenReturn(text);
        if (promptTokens != null) {
            ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
            Usage usage = mock(Usage.class);
            when(resp.getMetadata()).thenReturn(metadata);
            when(metadata.getUsage()).thenReturn(usage);
            when(usage.getPromptTokens()).thenReturn(promptTokens);
            when(usage.getCompletionTokens()).thenReturn(completionTokens);
        }
        return resp;
    }
}
