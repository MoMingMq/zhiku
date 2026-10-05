package org.zhiku.ai.knowledge.ingest.chunking;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归字符兜底：对仍超 maxChars 的块按分隔符优先级（\n\n → \n → 。！？ → 空格 → 硬切）递归切分，带重叠
 * <p>表格/代码块跳过（原子保留）。
 *
 * @author zhiku
 */
@Order(2)
@Component
public class RecursiveFallback implements ChunkStage {

    private static final String[] SEPARATOR_PRIORITY = {"\n\n", "\n", "。", "！", "？", "！", "?", "；", ";", " "};

    @Override
    public List<ChunkDraft> process(List<ChunkDraft> input, ChunkParams params) {
        List<ChunkDraft> output = new ArrayList<>();
        for (ChunkDraft draft : input) {
            String type = draft.getBlockType();
            if ("table".equals(type) || "table_continuation".equals(type) || "code_block".equals(type)) {
                output.add(draft);
                continue;
            }
            String content = draft.getContent() == null ? "" : draft.getContent();
            if (content.length() <= params.getMaxChars()) {
                output.add(draft);
                continue;
            }
            output.addAll(splitRecursive(content, draft, params));
        }
        return output;
    }

    private List<ChunkDraft> splitRecursive(String content, ChunkDraft source, ChunkParams params) {
        List<ChunkDraft> parts = new ArrayList<>();
        int maxChars = params.getMaxChars();
        int overlap = (int) Math.round(maxChars * params.getOverlapRatio());
        String remaining = content;
        while (remaining.length() > maxChars) {
            int cut = findCut(remaining, maxChars);
            String part = remaining.substring(0, cut);
            parts.add(draftOf(part, source));
            int nextStart = Math.max(cut - Math.min(overlap, cut / 2), 1);
            remaining = remaining.substring(nextStart);
        }
        if (!remaining.isBlank()) {
            parts.add(draftOf(remaining, source));
        }
        return parts;
    }

    /**
     * 在 maxChars 内按分隔符优先级找最佳切点（取该分隔符的最后出现）
     */
    private int findCut(String text, int maxChars) {
        for (String separator : SEPARATOR_PRIORITY) {
            int windowEnd = Math.min(maxChars, text.length());
            int idx = text.lastIndexOf(separator, windowEnd);
            if (idx > maxChars / 2) {
                return idx + separator.length();
            }
        }
        return maxChars;
    }

    private ChunkDraft draftOf(String content, ChunkDraft source) {
        ChunkDraft draft = new ChunkDraft();
        draft.setContent(content);
        draft.setHeadingPath(source.getHeadingPath());
        draft.setStrategyTag("recursive");
        draft.setBlockType(source.getBlockType());
        return draft;
    }

}
