package org.zhiku.ai.knowledge.ingest;

/**
 * 文档入库状态机（kb_document.status）
 *
 * @author zhiku
 */
public final class DocumentStatus {

    /** 已上传（待处理） */
    public static final String UPLOADED = "0";
    /** 解析中 */
    public static final String PARSING = "1";
    /** 分块中 */
    public static final String CHUNKING = "2";
    /** 向量化中 */
    public static final String EMBEDDING = "3";
    /** 可用 */
    public static final String AVAILABLE = "4";
    /** 失败（error_msg 记录原因，可重试） */
    public static final String FAILED = "D";

    /** 进行中状态集合（删除保护用） */
    public static final java.util.Set<String> IN_PROGRESS =
        java.util.Set.of(PARSING, CHUNKING, EMBEDDING);

    private DocumentStatus() {
    }

}
