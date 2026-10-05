package org.zhiku.ai.knowledge.ingest.parser;

import org.junit.jupiter.api.Test;
import org.zhiku.common.core.exception.ServiceException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FlexmarkDocumentParser 单元测试（Markdown → DocumentIR 结构保真）
 *
 * @author zhiku
 */
class FlexmarkDocumentParserTest {

    private final FlexmarkDocumentParser parser = new FlexmarkDocumentParser();

    private DocumentIR parse(String md) {
        return parser.parse(new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8)), "test.md");
    }

    @Test
    void supportsMarkdownAndText() {
        assertTrue(parser.supports("md"));
        assertTrue(parser.supports("txt"));
        assertTrue(parser.supports("markdown"));
    }

    @Test
    void headingParagraphTableCodeBlockStructure() {
        String md = """
            # 第一章 概述

            系统概述文字。

            ## 1.1 背景

            背景说明文字。

            | 列A | 列B |
            |-----|-----|
            | 1   | 2   |

            ```java
            System.out.println("hi");
            ```

            ## 1.2 目标

            目标文字。
            """;
        DocumentIR ir = parse(md);
        List<IrBlock> blocks = ir.getBlocks();
        assertEquals(8, blocks.size());

        assertEquals(IrType.HEADING, blocks.get(0).getType());
        assertEquals(1, blocks.get(0).getLevel());

        assertEquals(IrType.PARAGRAPH, blocks.get(1).getType());
        assertEquals("系统概述文字。", blocks.get(1).getContent().trim());
        assertEquals("第一章 概述", blocks.get(1).getHeadingPath());

        assertEquals(IrType.HEADING, blocks.get(2).getType());
        assertEquals(2, blocks.get(2).getLevel());
        assertEquals("1.1 背景", blocks.get(2).getContent().trim());

        assertEquals(IrType.PARAGRAPH, blocks.get(3).getType());
        assertEquals("第一章 概述>1.1 背景", blocks.get(3).getHeadingPath());

        assertEquals(IrType.TABLE, blocks.get(4).getType());
        assertTrue(blocks.get(4).getContent().contains("列A"));
        assertTrue(blocks.get(4).getContent().contains("2"));
        assertEquals("第一章 概述>1.1 背景", blocks.get(4).getHeadingPath());

        assertEquals(IrType.CODE_BLOCK, blocks.get(5).getType());
        assertTrue(blocks.get(5).getContent().contains("System.out.println"));

        assertEquals(IrType.PARAGRAPH, blocks.get(7).getType());
        assertEquals("第一章 概述>1.2 目标", blocks.get(7).getHeadingPath());
        assertTrue(ir.getCharCount() > 0);
    }

    @Test
    void textFileSplitByBlankLine() {
        String txt = "第一段内容。\n第二行仍在第一段。\n\n第二段内容。";
        DocumentIR ir = parser.parse(new ByteArrayInputStream(txt.getBytes(StandardCharsets.UTF_8)), "a.txt");
        List<IrBlock> blocks = ir.getBlocks();
        assertEquals(2, blocks.size());
        assertEquals(IrType.PARAGRAPH, blocks.get(0).getType());
        assertTrue(blocks.get(0).getContent().contains("第二行仍在第一段"));
        assertEquals("", blocks.get(0).getHeadingPath());
    }

    @Test
    void emptyContentThrows() {
        assertThrows(ServiceException.class, () -> parse(""));
        assertThrows(ServiceException.class, () -> parse("   \n \n"));
    }

}
