package org.zhiku.ai.knowledge.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 检索请求（M4 应用对话与命中测试共用）
 *
 * @author zhiku
 */
@Data
public class RetrievalQuery {

    /**
     * 检索范围（多库并集）
     */
    private List<Long> kbIds;

    /**
     * 查询文本
     */
    private String text;

    /**
     * 返回条数
     */
    private int topK = 5;

    /**
     * 是否启用 Rerank 重排（存在默认 Rerank 模型时生效）
     */
    private boolean rerankEnabled = false;
    /**
     * 相似度阈值（0-1，低于该分数的命中过滤；0=不过滤）
     */
    private float minScore = 0f;

}
