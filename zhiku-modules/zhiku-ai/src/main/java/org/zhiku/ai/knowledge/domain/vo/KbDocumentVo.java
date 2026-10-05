package org.zhiku.ai.knowledge.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.zhiku.ai.knowledge.domain.KbDocument;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 知识库文档视图对象 kb_document
 *
 * @author zhiku
 */
@Data
@AutoMapper(target = KbDocument.class)
public class KbDocumentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文档ID
     */
    private Long documentId;

    /**
     * 所属知识库ID
     */
    private Long datasetId;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 格式
     */
    private String fileType;

    /**
     * 状态机（0已上传 1解析中 2分块中 3向量化中 4可用 D失败）
     */
    private String status;

    /**
     * 失败原因
     */
    private String errorMsg;

    /**
     * 正文总字符数
     */
    private Integer charCount;

    /**
     * 创建时间
     */
    private Date createTime;

}
