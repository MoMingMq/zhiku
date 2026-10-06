package org.zhiku.ai.knowledge.ingest.chunking;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StructureSplitter 单元测试（纯函数：section 聚合/原子块/超长表格行组切分）
 *
 * @author zhiku
 */
class StructureSplitterTest {

    private final StructureSplitter splitter = new StructureSplitter();

    private ChunkDraft draft(String type, String content, String path) {
        ChunkDraft d = new ChunkDraft();
        d.setBlockType(type);
        d.setContent(content);
        d.setHeadingPath(path);
        return d;
    }

    @Test
    void headingMergedIntoSection() {
        // 解析器语义：heading 块的 headingPath 为父路径（一级标题父路径为空）
        List<ChunkDraft> result = splitter.process(List.of(
            draft("heading", "第一章", ""),
            draft("paragraph", "概述文字。", "第一章")), new ChunkParams());
        assertEquals(1, result.size());
        assertEquals("section", result.get(0).getBlockType());
        assertTrue(result.get(0).getContent().contains("第一章"));
        assertTrue(result.get(0).getContent().contains("概述文字。"));
        assertEquals("第一章", result.get(0).getHeadingPath());
    }

    @Test
    void secondLevelHeadingBuildsHierarchicalPath() {
        // h1 章标题（父路径空）→ h2 节标题（父路径=章名）→ 节下段落，section 路径应含完整层级
        List<ChunkDraft> result = splitter.process(List.of(
            draft("heading", "第一章", ""),
            draft("paragraph", "章引言。", "第一章"),
            draft("heading", "1.1 范围", "第一章"),
            draft("paragraph", "节正文。", "第一章>1.1 范围")), new ChunkParams());
        assertEquals(2, result.size());
        assertEquals("第一章", result.get(0).getHeadingPath());
        assertEquals("第一章>1.1 范围", result.get(1).getHeadingPath());
        assertTrue(result.get(1).getContent().startsWith("1.1 范围"));
    }

    @Test
    void smallTableKeptAtomic() {
        String table = "| a | b |\n| 1 | 2 |\n| 3 | 4 |";
        List<ChunkDraft> result = splitter.process(List.of(
            draft("heading", "标题", ""),
            draft("paragraph", "段落", "标题"),
            draft("table", table, "标题")), new ChunkParams());
        assertEquals(2, result.size());
        assertEquals("table", result.get(1).getBlockType());
        assertEquals(table, result.get(1).getContent());
    }

    @Test
    void oversizedParagraphStaysSingleBlock() {
        String longText = "字".repeat(800);
        List<ChunkDraft> result = splitter.process(List.of(draft("paragraph", longText, "")), new ChunkParams());
        assertEquals(1, result.size(), "超长段落不在结构阶段切分，留给递归兜底");
        assertEquals(longText, result.get(0).getContent());
    }

    @Test
    void oversizedTableSplitByRowGroups() {
        ChunkParams params = new ChunkParams();
        params.setMaxChars(60);
        StringBuilder table = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            if (i > 1) {
                table.append('\n');
            }
            table.append("| 行").append(i).append(" | 内容内容内容内容内容内容内容内容内容内容内容 |");
        }
        List<ChunkDraft> result = splitter.process(List.of(draft("table", table.toString(), "")), params);
        assertTrue(result.size() >= 2, "超长表格应按行组切分，实际 " + result.size());
        assertEquals("table", result.get(0).getBlockType());
        for (int i = 1; i < result.size(); i++) {
            assertEquals("table_continuation", result.get(i).getBlockType());
        }
        result.forEach(r -> assertTrue(r.getContent().length() <= 60, "每行组不超上限"));
    }

    @Test
    void codeBlockNeverSplit() {
        ChunkParams params = new ChunkParams();
        params.setMaxChars(50);
        String code = "x\n".repeat(100);
        List<ChunkDraft> result = splitter.process(List.of(draft("code_block", code.strip(), "")), params);
        assertEquals(1, result.size());
        assertEquals("code_block", result.get(0).getBlockType());
    }

    @Test
    void paragraphsAggregatedWithinLimit() {
        ChunkParams params = new ChunkParams();
        params.setMaxChars(100);
        List<ChunkDraft> result = splitter.process(List.of(
            draft("paragraph", "a".repeat(40), "p"),
            draft("paragraph", "b".repeat(40), "p"),
            draft("paragraph", "c".repeat(40), "p")), params);
        assertEquals(2, result.size(), "前两段聚合（80+1<100），第三段溢出新块");
        assertTrue(result.get(0).getContent().contains("a"));
        assertTrue(result.get(0).getContent().contains("b"));
        assertTrue(result.get(1).getContent().startsWith("c"));
    }

}
