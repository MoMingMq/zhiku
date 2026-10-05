package org.zhiku.ai.knowledge.ingest.chunking;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义聚合 Stage（高质档）：块内句子级 embedding，相邻句余弦相似度低于阈值处断开聚合
 * <p>非 Spring 单例——运行时由管线按知识库绑定的 embedding 模型构造（EmbeddingFn 绑定 modelId）。
 *
 * @author zhiku
 */
public class SemanticAggregator implements ChunkStage {

    /**
     * 向量化函数（绑定具体 modelId 的适配：texts 批量 → 向量）
     */
    @FunctionalInterface
    public interface EmbeddingFn {

        /**
         * 批量向量化（实现侧负责分批与重试）
         */
        List<float[]> embed(List<String> texts);
    }

    private final EmbeddingFn embeddingFn;

    public SemanticAggregator(EmbeddingFn embeddingFn) {
        this.embeddingFn = embeddingFn;
    }

    @Override
    public boolean supports(ChunkParams params) {
        return params.semanticEnabled();
    }

    @Override
    public List<ChunkDraft> process(List<ChunkDraft> input, ChunkParams params) {
        List<ChunkDraft> output = new ArrayList<>();
        for (ChunkDraft draft : input) {
            String type = draft.getBlockType();
            if ("table".equals(type) || "table_continuation".equals(type) || "code_block".equals(type)
                || "heading".equals(type)) {
                output.add(draft);
                continue;
            }
            String content = draft.getContent() == null ? "" : draft.getContent();
            // 语义档对多句块做主题断点聚合（不限超长——主题边界与长度无关；单句块透传）
            if (countSentences(content) < 2) {
                output.add(draft);
                continue;
            }
            output.addAll(aggregate(content, draft, params));
        }
        return output;
    }

    private int countSentences(String content) {
        return splitSentences(content).size();
    }

    private List<ChunkDraft> aggregate(String content, ChunkDraft source, ChunkParams params) {
        List<String> sentences = splitSentences(content);
        List<Integer> breakpoints = findBreakpoints(sentences, params);
        List<ChunkDraft> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int nextBreak = 0;
        for (int i = 0; i < sentences.size(); i++) {
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(sentences.get(i));
            if (i == breakpoints.get(nextBreak) || i == sentences.size() - 1) {
                ChunkDraft part = new ChunkDraft();
                part.setContent(current.toString());
                part.setHeadingPath(source.getHeadingPath());
                part.setStrategyTag("semantic");
                part.setBlockType(source.getBlockType());
                parts.add(part);
                current = new StringBuilder();
                nextBreak = Math.min(nextBreak + 1, breakpoints.size() - 1);
            }
        }
        if (current.length() > 0) {
            ChunkDraft part = new ChunkDraft();
            part.setContent(current.toString());
            part.setHeadingPath(source.getHeadingPath());
            part.setStrategyTag("semantic");
            part.setBlockType(source.getBlockType());
            parts.add(part);
        }
        return parts;
    }

    /**
     * 句子切分（保留分隔符）
     */
    private List<String> splitSentences(String content) {
        List<String> sentences = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            current.append(c);
            if (c == '。' || c == '！' || c == '？' || c == '!' || c == '?' || c == ';' || c == '；' || c == '\n') {
                String sentence = current.toString().strip();
                if (!sentence.isEmpty()) {
                    sentences.add(sentence);
                }
                current = new StringBuilder();
            }
        }
        String tail = current.toString().strip();
        if (!tail.isEmpty()) {
            sentences.add(tail);
        }
        return sentences;
    }

    /**
     * 语义断点：相邻句向量余弦相似度低于阈值处（返回断点句下标列表，末哨兵 -1 占位）
     */
    private List<Integer> findBreakpoints(List<String> sentences, ChunkParams params) {
        List<Integer> breakpoints = new ArrayList<>();
        if (sentences.size() <= 1) {
            breakpoints.add(-1);
            return breakpoints;
        }
        List<float[]> vectors = embeddingFn.embed(sentences);
        for (int i = 0; i < sentences.size() - 1; i++) {
            double similarity = cosine(vectors.get(i), vectors.get(i + 1));
            if (similarity < params.getSemanticThreshold()) {
                breakpoints.add(i);
            }
        }
        breakpoints.add(-1);
        return breakpoints;
    }

    private double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

}
