package org.zhiku.ai.knowledge.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 问题关联分段视图（问题详情抽屉/右侧面板与绑定弹窗用）
 *
 * @author zhiku
 */
@Data
public class KbProblemChunkVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long chunkId;

    private Long documentId;

    private String documentName;

    /**
     * 分段标题
     */
    private String title;

    /**
     * 分段正文
     */
    private String content;

    /**
     * 文档内序号
     */
    private Integer seq;

    private Integer charCount;

}
