package org.zhiku.ai.knowledge.ingest.parser;

import java.io.InputStream;

/**
 * 文档解析器：统一产出 DocumentIR（新增格式 = 新实现类）
 *
 * @author zhiku
 */
public interface DocumentParser {

    /**
     * 是否支持该文件格式（pdf/docx/xlsx/md/txt/html）
     */
    boolean supports(String fileType);

    /**
     * 解析为结构化中间表示；无文字内容（如扫描件）抛 ServiceException
     */
    DocumentIR parse(InputStream in, String fileName);

}
