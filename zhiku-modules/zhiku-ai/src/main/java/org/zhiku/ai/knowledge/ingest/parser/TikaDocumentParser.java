package org.zhiku.ai.knowledge.ingest.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToXMLContentHandler;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.zhiku.common.core.exception.ServiceException;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tika 解析器（PDF/DOCX/XLSX/HTML → 结构化 XHTML → DocumentIR）
 * <p>Tika 的 XHTML 输出自带 h1-h6/p/table/pre 结构标签，遍历映射为 IrBlock；
 * 表格重建为 Markdown 管道表（原子块），空文档（扫描件）明确报错。
 *
 * @author zhiku
 */
@Slf4j
@Component
public class TikaDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String fileType) {
        return "pdf".equalsIgnoreCase(fileType) || "docx".equalsIgnoreCase(fileType)
            || "xlsx".equalsIgnoreCase(fileType) || "html".equalsIgnoreCase(fileType);
    }

    @Override
    public DocumentIR parse(InputStream in, String fileName) {
        String xhtml = toXhtml(in);
        List<IrBlock> blocks = walkXhtml(xhtml);
        int charCount = blocks.stream()
            .filter(b -> b.getType() != IrType.HEADING)
            .mapToInt(b -> b.getContent() == null ? 0 : b.getContent().length())
            .sum();
        if (charCount == 0) {
            throw new ServiceException("文档无文字内容（扫描件需 OCR，本期不支持）");
        }
        DocumentIR ir = new DocumentIR();
        ir.setBlocks(blocks);
        ir.setCharCount(charCount);
        return ir;
    }

    /**
     * Tika 解析为 XHTML 字符串
     */
    private String toXhtml(InputStream in) {
        try {
            AutoDetectParser parser = new AutoDetectParser();
            ToXMLContentHandler handler = new ToXMLContentHandler();
            parser.parse(in, handler, new Metadata(), new ParseContext());
            return handler.toString();
        } catch (Exception e) {
            throw new ServiceException("文档解析失败：" + e.getMessage());
        }
    }

    /**
     * 遍历 XHTML body：块级元素映射 IrBlock（table/pre 收集后不再深入其内部）
     */
    private List<IrBlock> walkXhtml(String xhtml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Tika 输出为本地生成的无命名空间前缀 XHTML，关闭 ns 感知按标签名匹配
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(xhtml)));
            List<IrBlock> blocks = new ArrayList<>();
            String[] headingStack = new String[7];
            collect(document.getDocumentElement(), blocks, headingStack);
            return blocks;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("文档结构解析失败：" + e.getMessage());
        }
    }

    private void collect(Element root, List<IrBlock> blocks, String[] headingStack) {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }
            String tag = element.getTagName();
            if (tag.length() == 2 && tag.charAt(0) == 'h' && tag.charAt(1) >= '1' && tag.charAt(1) <= '6') {
                int level = tag.charAt(1) - '0';
                String title = element.getTextContent().strip();
                if (title.isEmpty()) {
                    continue;
                }
                headingStack[level] = title;
                Arrays.fill(headingStack, level + 1, headingStack.length, null);
                blocks.add(block(IrType.HEADING, title, path(headingStack, level - 1), level));
            } else if ("p".equals(tag)) {
                String content = element.getTextContent().strip();
                if (!content.isEmpty()) {
                    blocks.add(block(IrType.PARAGRAPH, content, path(headingStack, 6), 0));
                }
            } else if ("table".equals(tag)) {
                String content = rebuildTable(element);
                if (!content.isEmpty()) {
                    blocks.add(block(IrType.TABLE, content, path(headingStack, 6), 0));
                }
            } else if ("pre".equals(tag)) {
                String content = element.getTextContent().strip();
                if (!content.isEmpty()) {
                    blocks.add(block(IrType.CODE_BLOCK, content, path(headingStack, 6), 0));
                }
            } else {
                // 容器元素（body/div/main 等）递归下钻
                collect(element, blocks, headingStack);
            }
        }
    }

    /**
     * 表格重建为 Markdown 管道表（首行为表头，其后插入 GFM 必需的分隔行；跨行合并单元格按同行收集）
     * <p>GFM 硬约束：单元格必须单行——嵌套表格先拍平为纯文本、单元格内空白（换行/制表符）压成单空格。
     */
    private String rebuildTable(Element table) {
        flattenNestedTables(table);
        StringBuilder sb = new StringBuilder();
        NodeList rows = table.getElementsByTagName("tr");
        boolean headerWritten = false;
        for (int i = 0; i < rows.getLength(); i++) {
            Element row = (Element) rows.item(i);
            NodeList cells = row.getElementsByTagName("td");
            if (cells.getLength() == 0) {
                cells = row.getElementsByTagName("th");
            }
            List<String> parts = new ArrayList<>();
            for (int j = 0; j < cells.getLength(); j++) {
                String text = cells.item(j).getTextContent().replaceAll("\\s+", " ").strip();
                parts.add(text.isEmpty() ? " " : text);
            }
            if (!parts.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append("| ").append(String.join(" | ", parts)).append(" |");
                if (!headerWritten) {
                    sb.append('\n')
                        .append("| ").append(String.join(" | ", Collections.nCopies(parts.size(), "---"))).append(" |");
                    headerWritten = true;
                }
            }
        }
        return sb.toString();
    }

    /**
     * 嵌套表格拍平：内层 table 整体替换为其纯文本（空白压平）。
     * getElementsByTagName 按后代递归匹配，不先移除内层 table 会把内层 tr/td 混入外层行。
     */
    private void flattenNestedTables(Element table) {
        NodeList nested = table.getElementsByTagName("table");
        for (int i = nested.getLength() - 1; i >= 0; i--) {
            Element inner = (Element) nested.item(i);
            String flat = inner.getTextContent().replaceAll("\\s+", " ").strip();
            org.w3c.dom.Text textNode = table.getOwnerDocument().createTextNode(" " + flat + " ");
            if (inner.getParentNode() != null) {
                inner.getParentNode().replaceChild(textNode, inner);
            }
        }
    }

    private String path(String[] headingStack, int maxLevel) {
        List<String> parts = new ArrayList<>();
        for (int i = 1; i <= Math.min(maxLevel, 6); i++) {
            if (headingStack[i] != null) {
                parts.add(headingStack[i]);
            }
        }
        return String.join(">", parts);
    }

    private IrBlock block(IrType type, String content, String headingPath, int level) {
        IrBlock b = new IrBlock();
        b.setType(type);
        b.setContent(content);
        b.setHeadingPath(headingPath == null ? "" : headingPath);
        b.setLevel(level);
        return b;
    }

}
