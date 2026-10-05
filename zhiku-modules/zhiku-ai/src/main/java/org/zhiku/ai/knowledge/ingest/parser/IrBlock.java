package org.zhiku.ai.knowledge.ingest.parser;

import lombok.Data;

/**
 * 结构块（标题开新 section，后续内容挂其下；表格/代码块原子保留）
 *
 * @author zhiku
 */
@Data
public class IrBlock {

    private IrType type;

    /**
     * 仅 HEADING：标题层级 1-6
     */
    private int level;

    /**
     * HEADING=标题文本；PARAGRAPH=正文；TABLE=Markdown 管道表重建；CODE_BLOCK=代码原文
     */
    private String content;

    /**
     * 所属标题路径（如「第一章>1.1」，顶级为空串）
     */
    private String headingPath;

}
