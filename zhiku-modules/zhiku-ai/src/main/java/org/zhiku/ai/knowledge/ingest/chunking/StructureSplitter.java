package org.zhiku.ai.knowledge.ingest.chunking;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构感知切分：IR 初始块 → section 候选块
 * <p>规则：标题并入 section 首块正文与标题路径（父路径 + 标题自身，保层级）；连续段落聚合至上限；表格/代码块原子保留；
 * 超长表格按行组切分并标记续块；代码块即使超长也不切（递归兜底同样跳过）。
 *
 * @author zhiku
 */
@Order(1)
@Component
public class StructureSplitter implements ChunkStage {

    @Override
    public List<ChunkDraft> process(List<ChunkDraft> input, ChunkParams params) {
        List<ChunkDraft> output = new ArrayList<>();
        StringBuilder sectionBuffer = null;
        String sectionPath = "";
        for (ChunkDraft draft : input) {
            switch (draft.getBlockType()) {
                case "heading" -> {
                    flush(output, sectionBuffer, sectionPath);
                    // 解析器给 heading 块的 headingPath 是父路径，此处并入标题自身得到完整层级路径
                    String parent = draft.getHeadingPath() == null ? "" : draft.getHeadingPath();
                    String title = draft.getContent() == null ? "" : draft.getContent();
                    sectionBuffer = new StringBuilder(title);
                    sectionPath = parent.isEmpty() ? title : parent + ">" + title;
                }
                case "paragraph" -> {
                    if (sectionBuffer == null) {
                        sectionBuffer = new StringBuilder();
                        sectionPath = draft.getHeadingPath() == null ? "" : draft.getHeadingPath();
                    }
                    String text = draft.getContent() == null ? "" : draft.getContent();
                    if (sectionBuffer.length() > 0 && sectionBuffer.length() + text.length() + 1 > params.getMaxChars()) {
                        flush(output, sectionBuffer, sectionPath);
                        sectionBuffer = new StringBuilder();
                    }
                    if (sectionBuffer.length() > 0) {
                        sectionBuffer.append('\n');
                    }
                    sectionBuffer.append(text);
                }
                case "table" -> {
                    flush(output, sectionBuffer, sectionPath);
                    sectionBuffer = null;
                    appendTable(output, draft, params);
                }
                case "code_block" -> {
                    flush(output, sectionBuffer, sectionPath);
                    sectionBuffer = null;
                    // 代码块原子保留，超长不切（递归兜底同样跳过）
                    ChunkDraft code = copy(draft);
                    code.setHeadingPath(sectionPath.isEmpty() ? draft.getHeadingPath() : sectionPath);
                    output.add(code);
                }
                default -> {
                    // 未知类型透传
                    output.add(copy(draft));
                }
            }
        }
        flush(output, sectionBuffer, sectionPath);
        return output;
    }

    /**
     * 超长表格按行组切分（每行组 ≤ maxChars，第二块起标记 table_continuation）
     */
    private void appendTable(List<ChunkDraft> output, ChunkDraft table, ChunkParams params) {
        String content = table.getContent() == null ? "" : table.getContent();
        if (content.length() <= params.getMaxChars()) {
            output.add(copy(table));
            return;
        }
        String[] lines = content.split("\n");
        StringBuilder group = new StringBuilder();
        boolean first = true;
        for (String line : lines) {
            if (group.length() > 0 && group.length() + line.length() + 1 > params.getMaxChars()) {
                ChunkDraft part = copy(table);
                part.setContent(group.toString());
                part.setBlockType(first ? "table" : "table_continuation");
                output.add(part);
                first = false;
                group = new StringBuilder();
            }
            if (group.length() > 0) {
                group.append('\n');
            }
            group.append(line);
        }
        if (group.length() > 0) {
            ChunkDraft part = copy(table);
            part.setContent(group.toString());
            part.setBlockType(first ? "table" : "table_continuation");
            output.add(part);
        }
    }

    private void flush(List<ChunkDraft> output, StringBuilder buffer, String path) {
        if (buffer != null && buffer.length() > 0) {
            ChunkDraft draft = new ChunkDraft();
            draft.setContent(buffer.toString());
            draft.setHeadingPath(path);
            draft.setStrategyTag("structure");
            draft.setBlockType("section");
            output.add(draft);
        }
    }

    private ChunkDraft copy(ChunkDraft source) {
        ChunkDraft draft = new ChunkDraft();
        draft.setContent(source.getContent());
        draft.setHeadingPath(source.getHeadingPath());
        draft.setStrategyTag("structure");
        draft.setBlockType(source.getBlockType());
        return draft;
    }

}
