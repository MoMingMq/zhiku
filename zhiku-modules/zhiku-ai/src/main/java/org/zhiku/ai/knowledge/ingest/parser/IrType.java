package org.zhiku.ai.knowledge.ingest.parser;

/**
 * 结构块类型
 *
 * @author zhiku
 */
public enum IrType {

    /** 标题 */
    HEADING,
    /** 段落 */
    PARAGRAPH,
    /** 表格（原子块，分块时不切断） */
    TABLE,
    /** 代码块（原子块，分块时不切断） */
    CODE_BLOCK

}
