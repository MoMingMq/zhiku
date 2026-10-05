package org.zhiku.ai.knowledge.ingest.chunking;

import lombok.Data;

/**
 * 分块草稿（管线中间产物，入库时映射 KbChunk）
 *
 * @author zhiku
 */
@Data
public class ChunkDraft {

    /** 分块正文 */
    private String content;

    /** 标题路径 */
    private String headingPath;

    /** 产出标记：structure / semantic / recursive */
    private String strategyTag;

    /** 字符数 */
    private int charCount;

    /** 结构块类型：section / table / code_block / table_continuation */
    private String blockType;

}
