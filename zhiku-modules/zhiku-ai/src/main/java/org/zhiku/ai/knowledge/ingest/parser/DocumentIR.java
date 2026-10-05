package org.zhiku.ai.knowledge.ingest.parser;

import lombok.Data;

import java.util.List;

/**
 * 文档解析的统一中间表示：结构块有序列表（表格/代码块为原子块）
 *
 * @author zhiku
 */
@Data
public class DocumentIR {

    /**
     * 结构块序列（保持文档顺序）
     */
    private List<IrBlock> blocks;

    /**
     * 正文总字符数
     */
    private int charCount;

}
