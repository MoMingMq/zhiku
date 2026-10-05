package org.zhiku.ai.knowledge.ingest.chunking;

import lombok.Data;

/**
 * 分块参数（kb_dataset.chunk_params JSON）
 *
 * @author zhiku
 */
@Data
public class ChunkParams {

    /** 分块模式：structure（默认档）/ structure_semantic（高质档，开启语义聚合） */
    public static final String MODE_STRUCTURE = "structure";
    public static final String MODE_STRUCTURE_SEMANTIC = "structure_semantic";

    /** 分块模式 */
    private String mode = MODE_STRUCTURE;

    /** 单块最大字符数（≈500 token，中文近似） */
    private int maxChars = 700;

    /** 递归兜底切分的重叠比例（0-0.5） */
    private double overlapRatio = 0.15;

    /** 语义聚合：相邻句余弦相似度低于此值断开（仅 structure_semantic 模式） */
    private double semanticThreshold = 0.75;

    /**
     * 从 JSON 解析（非法/为空返回默认值）
     */
    public static ChunkParams fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new ChunkParams();
        }
        try {
            return org.zhiku.common.json.utils.JsonUtils.parseObject(json, ChunkParams.class);
        } catch (Exception e) {
            return new ChunkParams();
        }
    }

    public boolean semanticEnabled() {
        return MODE_STRUCTURE_SEMANTIC.equalsIgnoreCase(mode);
    }

}
