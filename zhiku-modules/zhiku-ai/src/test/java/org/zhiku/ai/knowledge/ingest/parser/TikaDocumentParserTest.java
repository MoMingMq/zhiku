package org.zhiku.ai.knowledge.ingest.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.zhiku.common.core.exception.ServiceException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TikaDocumentParser 单元测试（夹具用 POI/PDFBox 代码生成，不提交二进制文件）
 *
 * @author zhiku
 */
class TikaDocumentParserTest {

    private final TikaDocumentParser parser = new TikaDocumentParser();

    @Test
    void supportsFourFormats() {
        assertTrue(parser.supports("pdf"));
        assertTrue(parser.supports("docx"));
        assertTrue(parser.supports("xlsx"));
        assertTrue(parser.supports("html"));
    }

    @Test
    void htmlParsesHeadingsAndTable() {
        String html = """
            <html><body>
            <h1>制度标题</h1>
            <p>制度说明文字。</p>
            <table><tr><th>项目</th><th>数值</th></tr><tr><td>上限</td><td>100</td></tr></table>
            </body></html>
            """;
        DocumentIR ir = parser.parse(new ByteArrayInputStream(html.getBytes()), "a.html");
        List<IrBlock> blocks = ir.getBlocks();
        assertEquals(3, blocks.size());
        assertEquals(IrType.HEADING, blocks.get(0).getType());
        assertEquals("制度标题", blocks.get(0).getContent());
        assertEquals(IrType.PARAGRAPH, blocks.get(1).getType());
        assertEquals("制度标题", blocks.get(1).getHeadingPath());
        assertEquals(IrType.TABLE, blocks.get(2).getType());
        assertTrue(blocks.get(2).getContent().contains("上限"));
        assertTrue(blocks.get(2).getContent().contains("100"));
        // GFM 表格语法：首行表头后必须跟分隔行，否则前端渲染为纯文本
        String[] lines = blocks.get(2).getContent().split("\n");
        assertEquals("| --- | --- |", lines[1], "第二行应为两列分隔行");
    }

    @Test
    void docxParsesParagraphAndTable() throws IOException {
        byte[] docx = buildDocx();
        DocumentIR ir = parser.parse(new ByteArrayInputStream(docx), "a.docx");
        List<IrBlock> blocks = ir.getBlocks();
        assertTrue(blocks.size() >= 2, "docx 至少产出标题/段落/表格块，实际 " + blocks.size());
        assertTrue(blocks.stream().anyMatch(b -> b.getType() == IrType.PARAGRAPH && b.getContent().contains("正文段落")),
            "应包含正文段落块");
        assertTrue(blocks.stream().anyMatch(b -> b.getType() == IrType.TABLE && b.getContent().contains("单元格A")),
            "应包含表格块且内容保留");
        assertTrue(blocks.stream().anyMatch(b -> b.getType() == IrType.TABLE
                && b.getContent().split("\n")[1].equals("| --- | --- |")),
            "docx 表格第二行应为分隔行（GFM 语法）");
    }

    @Test
    void pdfParsesParagraphs() throws IOException {
        byte[] pdf = buildPdf();
        DocumentIR ir = parser.parse(new ByteArrayInputStream(pdf), "a.pdf");
        List<IrBlock> blocks = ir.getBlocks();
        assertTrue(blocks.stream().anyMatch(b -> b.getType() == IrType.PARAGRAPH), "PDF 应产出段落块");
        assertTrue(ir.getCharCount() > 0);
    }

    @Test
    void nestedTableFlattenedAndCellSingleLine() {
        // 嵌套表格 + 单元格内换行：GFM 要求单元格单行，内层表格拍平为纯文本
        String html = """
            <html><body>
            <table>
            <tr><th>外层</th><th>说明</th></tr>
            <tr><td>普通单元格</td><td><table><tr><td>内层A</td><td>内层B</td></tr></table></td></tr>
            <tr><td>带换行
            文本</td><td>值</td></tr>
            </table>
            </body></html>
            """;
        DocumentIR ir = parser.parse(new ByteArrayInputStream(html.getBytes()), "a.html");
        IrBlock table = ir.getBlocks().stream().filter(b -> b.getType() == IrType.TABLE).findFirst().orElseThrow();
        String[] lines = table.getContent().split("\n");
        assertEquals(4, lines.length, "表头+分隔+两数据行，实际：" + table.getContent());
        assertEquals("| --- | --- |", lines[1]);
        assertEquals("| 普通单元格 | 内层A 内层B |", lines[2], "嵌套表格应拍平为单行文本并入单元格");
        assertEquals("| 带换行 文本 | 值 |", lines[3], "单元格内换行应压成单空格");
    }

    @Test
    void emptyContentThrows() {
        String html = "<html><body></body></html>";
        assertThrows(ServiceException.class,
            () -> parser.parse(new ByteArrayInputStream(html.getBytes()), "a.html"));
    }

    /**
     * 生成含标题/正文/两行表格的 DOCX
     */
    private byte[] buildDocx() throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph heading = doc.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("文档标题");
            XWPFParagraph body = doc.createParagraph();
            body.createRun().setText("这是正文段落内容。");
            XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("表头1");
            table.getRow(0).getCell(1).setText("表头2");
            table.getRow(1).getCell(0).setText("单元格A");
            table.getRow(1).getCell(1).setText("单元格B");
            doc.write(out);
            return out.toByteArray();
        }
    }

    /**
     * 生成两段文本的 PDF（PDFBox 3.x API：PDType1Font 构造器接受 FontName）
     */
    private byte[] buildPdf() throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (var contentStream = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 750);
                contentStream.showText("First paragraph line one.");
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Second line of content.");
                contentStream.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

}
