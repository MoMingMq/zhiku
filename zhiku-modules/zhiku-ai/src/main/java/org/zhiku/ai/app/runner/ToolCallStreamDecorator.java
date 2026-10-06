package org.zhiku.ai.app.runner;

import org.zhiku.ai.app.domain.vo.ToolCallRecordVo;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

/**
 * 工具调用流装饰器：包装 M3 的 ToolCallback——调用前发 tool_call 帧、调用后发 tool_result 帧，
 * 并收集调用记录供消息落库（M3 工厂零改动）。
 *
 * @author zhiku
 */
public class ToolCallStreamDecorator {

    /**
     * 装饰单个 callback
     *
     * @param inner     被包装的 callback（来自 AiToolCallbackFactory）
     * @param writer    SSE 帧写出器
     * @param collector 本次回答的工具调用记录收集器（落库用）
     */
    public static ToolCallback decorate(ToolCallback inner, ChatFrameWriter writer, List<ToolCallRecordVo> collector) {
        String name = inner.getToolDefinition().name();
        return new ToolCallback() {
            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return inner.getToolDefinition();
            }

            @Override
            public org.springframework.ai.tool.metadata.ToolMetadata getToolMetadata() {
                return inner.getToolMetadata();
            }

            @Override
            public String call(String toolInput) {
                return doCall(toolInput, null);
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return doCall(toolInput, toolContext);
            }

            private String doCall(String toolInput, ToolContext toolContext) {
                writer.toolCall(name, toolInput);
                ToolCallRecordVo record = new ToolCallRecordVo();
                record.setName(name);
                record.setArgs(toolInput);
                collector.add(record);
                String result;
                try {
                    result = toolContext == null ? inner.call(toolInput) : inner.call(toolInput, toolContext);
                    record.setSuccess(true);
                } catch (Exception e) {
                    result = "工具执行异常: " + e.getMessage();
                    record.setSuccess(false);
                }
                record.setResult(result);
                writer.toolResult(name, Boolean.TRUE.equals(record.getSuccess()), result);
                return result;
            }
        };
    }

    /**
     * 批量装饰
     */
    public static List<ToolCallback> decorateAll(List<ToolCallback> callbacks, ChatFrameWriter writer,
                                                 List<ToolCallRecordVo> collector) {
        return callbacks.stream().map(cb -> decorate(cb, writer, collector)).toList();
    }

    private ToolCallStreamDecorator() {
    }
}
