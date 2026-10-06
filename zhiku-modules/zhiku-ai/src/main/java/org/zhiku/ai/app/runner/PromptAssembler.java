package org.zhiku.ai.app.runner;

import org.zhiku.ai.knowledge.domain.vo.RetrievalHit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

import java.util.List;

/**
 * 提示词组装器（建造者风格核心为纯函数，可单测）
 * <p>上下文块：【参考资料】[n]（文档名 > 标题路径）原文，[n] 与 citation 数组同序。
 * <p>预算截断顺序：系统提示词(不裁) → 历史(从最旧丢整轮) → 检索条数(从低分丢) → 用户问题(不裁)。
 *
 * @author zhiku
 */
@Component
public class PromptAssembler {

    /**
     * token 简估：中文字符数 + 英文词数 × 1.3（防御性预算，非精确分词）
     */
    static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chinese = 0;
        int englishWords = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4e00 && c <= 0x9fa5) {
                chinese++;
                inWord = false;
            } else if (Character.isLetter(c)) {
                if (!inWord) {
                    englishWords++;
                    inWord = true;
                }
            } else {
                inWord = false;
            }
        }
        return chinese + (int) Math.ceil(englishWords * 1.3);
    }

    /**
     * 组装结果：contextBlock 为注入系统提示词的检索上下文块；kept* 为截断后保留数（citation 同步用）
     */
    public AssembleResult assemble(String systemPrompt, List<RetrievalHit> hits,
                                   List<ChatTurn> history, String question,
                                   int maxTurns, int tokenBudget) {
        // 1. 历史窗口：最近 maxTurns 轮
        List<ChatTurn> keptTurns = new ArrayList<>(history == null ? List.of() : history);
        if (keptTurns.size() > maxTurns) {
            keptTurns = new ArrayList<>(keptTurns.subList(keptTurns.size() - maxTurns, keptTurns.size()));
        }
        // 2. 检索按相似度降序（截断时从低分丢）
        List<RetrievalHit> sortedHits = new ArrayList<>(hits == null ? List.of() : hits);
        sortedHits.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));

        // 3. 预算裁剪：系统+问题固定占用，超预算先丢最旧轮再丢低分检索
        int systemCost = estimateTokens(systemPrompt) + estimateTokens(question);
        int used = systemCost;
        int keptTurnCount = keptTurns.size();
        // 从最旧开始丢整轮
        int turnIdx = 0;
        while (used + turnCost(keptTurns, turnIdx, keptTurnCount) > tokenBudget && turnIdx < keptTurnCount) {
            used += costOf(keptTurns.get(turnIdx));
            turnIdx++;
        }
        keptTurnCount -= turnIdx;
        // 检索条数裁剪（从低分丢）
        int hitCount = sortedHits.size();
        int hitCostAcc = 0;
        for (int i = 0; i < hitCount; i++) {
            int cost = costOf(sortedHits.get(i));
            if (i > 0 && used + hitCostAcc + cost > tokenBudget) {
                hitCount = i;
                break;
            }
            hitCostAcc += cost;
        }
        List<RetrievalHit> keptHits = sortedHits.subList(0, hitCount);
        List<ChatTurn> finalTurns = keptTurns.subList(keptTurns.size() - keptTurnCount, keptTurns.size());

        return new AssembleResult(buildContextBlock(keptHits), keptTurnCount, hitCount, finalTurns);
    }

    private int turnCost(List<ChatTurn> turns, int from, int to) {
        int cost = 0;
        for (int i = from; i < to; i++) {
            cost += costOf(turns.get(i));
        }
        return cost;
    }

    private int costOf(ChatTurn turn) {
        return estimateTokens(turn.question()) + estimateTokens(turn.answer());
    }

    private int costOf(RetrievalHit hit) {
        return estimateTokens(hit.getContent()) + estimateTokens(hit.getDocumentName()) + 10;
    }

    private String buildContextBlock(List<RetrievalHit> hits) {
        if (hits.isEmpty()) {
            return "【参考资料】\n（无相关资料）";
        }
        StringBuilder sb = new StringBuilder("【参考资料】\n");
        for (int i = 0; i < hits.size(); i++) {
            RetrievalHit hit = hits.get(i);
            sb.append('[').append(i + 1).append("]（")
                .append(hit.getDocumentName())
                .append(hit.getHeadingPath() == null || hit.getHeadingPath().isEmpty() ? "" : " > " + hit.getHeadingPath())
                .append("）\n").append(hit.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 组装结果（keptTurns 保留的历史轮，按时间升序）
     */
    public record AssembleResult(String contextBlock, int keptTurns, int keptHits, List<ChatTurn> turns) {
    }

}
