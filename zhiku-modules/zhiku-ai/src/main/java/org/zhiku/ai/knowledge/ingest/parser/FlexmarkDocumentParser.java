package org.zhiku.ai.knowledge.ingest.parser;

import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.ext.tables.TableRow;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Block;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import lombok.extern.slf4j.Slf4j;
import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Markdown/文本解析器（flexmark AST → DocumentIR；标题栈维护 headingPath，表格重建为管道表）
 *
 * @author zhiku
 */
@Slf4j
@Component
public class FlexmarkDocumentParser implements DocumentParser {

    private final Parser parser;

    public FlexmarkDocumentParser() {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, List.of(TablesExtension.create()));
        this.parser = Parser.builder(options).build();
    }

    @Override
    public boolean supports(String fileType) {
        return "md".equalsIgnoreCase(fileType) || "markdown".equalsIgnoreCase(fileType) || "txt".equalsIgnoreCase(fileType);
    }

    @Override
    public DocumentIR parse(InputStream in, String fileName) {
        String text = readText(in);
        List<IrBlock> blocks = isText(fileName)
            ? parsePlainText(text)
            : parseMarkdown(text);
        int charCount = blocks.stream()
            .filter(b -> b.getType() != IrType.HEADING)
            .mapToInt(b -> StringUtils.length(b.getContent()))
            .sum();
        if (charCount == 0) {
            throw new ServiceException("文档无文字内容（扫描件需 OCR，本期不支持）");
        }
        DocumentIR ir = new DocumentIR();
        ir.setBlocks(blocks);
        ir.setCharCount(charCount);
        return ir;
    }

    private boolean isText(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".txt");
    }

    private String readText(InputStream in) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new ServiceException("文档读取失败：" + e.getMessage());
        }
    }

    /**
     * 纯文本：按空行分段
     */
    private List<IrBlock> parsePlainText(String text) {
        List<IrBlock> blocks = new ArrayList<>();
        String[] parts = text.split("\\n\\s*\\n");
        for (String part : parts) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                blocks.add(block(IrType.PARAGRAPH, trimmed, "", 0));
            }
        }
        return blocks;
    }

    /**
     * Markdown：AST 顶层遍历，标题栈维护 headingPath
     */
    private List<IrBlock> parseMarkdown(String text) {
        Node document = parser.parse(text);
        List<IrBlock> blocks = new ArrayList<>();
        String[] headingStack = new String[7];
        for (Node node : document.getChildren()) {
            if (node instanceof com.vladsch.flexmark.ast.Heading heading) {
                int level = heading.getLevel();
                String title = heading.getText().toString().strip();
                headingStack[level] = title;
                Arrays.fill(headingStack, level + 1, headingStack.length, null);
                blocks.add(block(IrType.HEADING, title, currentPath(headingStack, level - 1), level));
            } else if (node instanceof com.vladsch.flexmark.ast.Paragraph paragraph) {
                String content = paragraph.getContentChars().toString().strip();
                if (!content.isEmpty()) {
                    blocks.add(block(IrType.PARAGRAPH, content, currentPath(headingStack, 6), 0));
                }
            } else if (node instanceof TableBlock table) {
                String content = rebuildTable(table);
                if (!content.isEmpty()) {
                    blocks.add(block(IrType.TABLE, content, currentPath(headingStack, 6), 0));
                }
            } else if (node instanceof com.vladsch.flexmark.ast.FencedCodeBlock code
                || node instanceof com.vladsch.flexmark.ast.IndentedCodeBlock indented) {
                String content = ((Block) node).getContentChars().toString();
                if (!content.isBlank()) {
                    blocks.add(block(IrType.CODE_BLOCK, content.stripTrailing(), currentPath(headingStack, 6), 0));
                }
            }
        }
        return blocks;
    }

    /**
     * 表格重建为 Markdown 管道表（保留原文语义，分块时原子保留）
     * <p>flexmark 表结构为 TableBlock > TableHead/TableBody > TableRow > TableCell，需两层遍历
     */
    private String rebuildTable(TableBlock table) {
        StringBuilder sb = new StringBuilder();
        for (Node section : table.getChildren()) {
            for (Node rowNode : section.getChildren()) {
                if (!(rowNode instanceof TableRow row)) {
                    continue;
                }
                List<String> cells = new ArrayList<>();
                for (Node cellNode : row.getChildren()) {
                    if (cellNode instanceof TableCell) {
                        cells.add(((TableCell) cellNode).getText().toString().strip());
                    }
                }
                if (!cells.isEmpty()) {
                    if (!sb.isEmpty()) {
                        sb.append('\n');
                    }
                    sb.append("| ").append(String.join(" | ", cells)).append(" |");
                }
            }
        }
        return sb.toString();
    }

    private String currentPath(String[] headingStack, int maxLevel) {
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
