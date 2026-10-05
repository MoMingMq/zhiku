package org.zhiku.ai.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.zhiku.common.mybatis.core.domain.BaseEntity;

/**
 * 知识库文档对象 kb_document
 *
 * @author zhiku
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_document")
public class KbDocument extends BaseEntity {

    /**
     * 文档ID
     */
    @TableId(value = "document_id")
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
     * 存储key（StorageService 相对路径）
     */
    private String filePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 格式（pdf/docx/xlsx/md/txt/html）
     */
    private String fileType;

    /**
     * 状态机（见 DocumentStatus）
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
     * 删除标志（0代表存在 2代表删除）
     */
    @TableLogic
    private String delFlag;

}
