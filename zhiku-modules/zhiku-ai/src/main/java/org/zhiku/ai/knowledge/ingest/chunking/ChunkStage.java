package org.zhiku.ai.knowledge.ingest.chunking;

import java.util.List;

/**
 * 分块 Stage：一次块变换（纯函数、无状态，可独立单测）
 * <p>新增分块算法 = 新实现类（策略可插拔，编排零改动）。
 *
 * @author zhiku
 */
public interface ChunkStage {

    /**
     * 该 Stage 是否参与本次管线（默认始终参与；语义档按 ChunkParams.mode 判定）
     */
    default boolean supports(ChunkParams params) {
        return true;
    }

    /**
     * 变换：输入块列表 + 参数 → 输出块列表
     */
    List<ChunkDraft> process(List<ChunkDraft> input, ChunkParams params);

}
