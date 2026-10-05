package org.zhiku.ai.knowledge.ingest.chunking;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RecursiveFallback 与 SemanticAggregator 单元测试
 *
 * @author zhiku
 */
class FallbackAndSemanticTest {

    private final RecursiveFallback fallback = new RecursiveFallback();

    private ChunkDraft draft(String type, String content) {
        ChunkDraft d = new ChunkDraft();
        d.setBlockType(type);
        d.setContent(content);
        d.setHeadingPath("");
        return d;
    }

    @Test
    void oversizedContentSplitWithOverlap() {
        ChunkParams params = new ChunkParams();
        params.setMaxChars(500);
        params.setOverlapRatio(0.2);
        String content = "句。" .repeat(600);
        List<ChunkDraft> result = fallback.process(List.of(draft("section", content)), params);
        assertTrue(result.size() >= 2, "600 句按 500 上限至少切两块");
        result.forEach(r -> {
            assertEquals("recursive", r.getStrategyTag());
            assertTrue(r.getContent().length() <= 500 + 50, "块长不超上限（切点容差）");
        });
        // 相邻块重叠：块2 开头应包含块1 尾部子串
        String tail = result.get(0).getContent()
            .substring(result.get(0).getContent().length() - 30);
        assertTrue(result.get(1).getContent().contains(tail.substring(10)),
            "重叠语义：后块应包含前块尾部内容");
    }

    @Test
    void separatorPriorityPrefersSentenceBreak() {
        ChunkParams params = new ChunkParams();
        params.setMaxChars(100);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            sb.append("这是第").append(i).append("句话，内容比较长。");
        }
        List<ChunkDraft> result = fallback.process(List.of(draft("section", sb.toString())), params);
        result.forEach(r -> assertTrue(r.getContent().endsWith("。") || r.getContent().length() <= 100,
            "切点优先落在句号边界"));
    }

    @Test
    void tableAndCodeSkipped() {
        ChunkParams params = new ChunkParams();
        params.setMaxChars(50);
        List<ChunkDraft> result = fallback.process(List.of(
            draft("table", "| a |\n".repeat(100)),
            draft("code_block", "x\n".repeat(100))), params);
        assertEquals(2, result.size(), "表格与代码块原子保留");
    }

    @Test
    void semanticAggregatorSplitsAtSimularityDrop() {
        // 4 句分两主题：前两句同向量、后两句正交向量 → 断点在句2|3 之间
        List<String> order = List.of("天气真好阳光明媚。", "适合出门散步运动。", "量子力学研究微观粒子。", "波函数描述其状态。");
        java.util.Map<String, float[]> vecMap = new java.util.HashMap<>();
        vecMap.put(order.get(0), new float[]{1, 0});
        vecMap.put(order.get(1), new float[]{1, 0});
        vecMap.put(order.get(2), new float[]{0, 1});
        vecMap.put(order.get(3), new float[]{0, 1});
        SemanticAggregator aggregator = new SemanticAggregator(texts ->
            texts.stream().map(t -> vecMap.getOrDefault(t, new float[]{0, 0})).collect(Collectors.toList()));
        ChunkParams params = new ChunkParams();
        params.setMaxChars(1000);
        params.setSemanticThreshold(0.75);
        String content = String.join("\n", order);
        List<ChunkDraft> result = aggregator.process(List.of(draft("section", content)), params);
        assertEquals(2, result.size(), "主题断点处应切为两块");
        assertEquals("semantic", result.get(0).getStrategyTag());
        assertTrue(result.get(0).getContent().contains("天气真好"));
        assertTrue(result.get(1).getContent().contains("量子力学"));
    }

    @Test
    void semanticDisabledByDefaultMode() {
        SemanticAggregator aggregator = new SemanticAggregator(texts -> List.of());
        assertTrue(!aggregator.supports(new ChunkParams()));
        ChunkParams semantic = new ChunkParams();
        semantic.setMode(ChunkParams.MODE_STRUCTURE_SEMANTIC);
        assertTrue(aggregator.supports(semantic));
    }

    @Test
    void smallBlockUnchanged() {
        ChunkParams params = new ChunkParams();
        ChunkDraft small = draft("section", "短内容。");
        List<ChunkDraft> result = fallback.process(List.of(small), params);
        assertEquals(1, result.size());
        assertEquals(small.getContent(), result.get(0).getContent());
    }

}
