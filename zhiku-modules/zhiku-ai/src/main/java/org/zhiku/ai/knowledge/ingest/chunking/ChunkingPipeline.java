package org.zhiku.ai.knowledge.ingest.chunking;

import lombok.RequiredArgsConstructor;
import org.zhiku.ai.knowledge.ingest.parser.DocumentIR;
import org.zhiku.ai.knowledge.ingest.parser.IrBlock;
import org.zhiku.ai.knowledge.ingest.parser.IrType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 分块管线编排：IR → 初始块 → 按 Stage 有序变换（新增算法 = 新 Stage 类，编排零改动）
 *
 * @author zhiku
 */
@RequiredArgsConstructor
@Component
public class ChunkingPipeline {

    private final List<ChunkStage> stages;

    /**
     * 执行分块：IR → 结构切分 →（语义档时）语义聚合 → 递归兜底
     */
    public List<ChunkDraft> run(DocumentIR ir, ChunkParams params) {
        List<ChunkDraft> current = toDrafts(ir);
        for (ChunkStage stage : stages) {
            if (stage.supports(params)) {
                current = stage.process(current, params);
            }
        }
        current.forEach(d -> d.setCharCount(d.getContent() == null ? 0 : d.getContent().length()));
        return current;
    }

    /**
     * IR → 初始块（标题并入策略由 StructureSplitter 处理，此处保留原始结构信息）
     */
    private List<ChunkDraft> toDrafts(DocumentIR ir) {
        List<ChunkDraft> drafts = new ArrayList<>();
        for (IrBlock block : ir.getBlocks()) {
            ChunkDraft draft = new ChunkDraft();
            draft.setContent(block.getContent());
            draft.setHeadingPath(block.getHeadingPath());
            draft.setStrategyTag("structure");
            draft.setBlockType(toBlockType(block.getType()));
            drafts.add(draft);
        }
        return drafts;
    }

    private String toBlockType(IrType type) {
        return switch (type) {
            case HEADING -> "heading";
            case PARAGRAPH -> "paragraph";
            case TABLE -> "table";
            case CODE_BLOCK -> "code_block";
        };
    }

}
