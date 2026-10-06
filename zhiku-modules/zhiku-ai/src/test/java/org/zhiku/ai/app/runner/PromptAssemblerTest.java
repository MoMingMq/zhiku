package org.zhiku.ai.app.runner;

import org.junit.jupiter.api.Test;
import org.zhiku.ai.knowledge.domain.vo.RetrievalHit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 提示词组装器单测（预算截断纯函数，简报点名必测）
 *
 * @author zhiku
 */
class PromptAssemblerTest {

    private final PromptAssembler assembler = new PromptAssembler();

    private RetrievalHit hit(String doc, String path, String content, float score) {
        RetrievalHit h = new RetrievalHit();
        h.setDocumentName(doc);
        h.setHeadingPath(path);
        h.setContent(content);
        h.setScore(score);
        return h;
    }

    private ChatTurn turn(String q, String a) {
        return new ChatTurn(q, a);
    }

    @Test
    void assemblesContextWithIndexedSources() {
        List<RetrievalHit> hits = List.of(
            hit("员工手册", "入职>流程", "第一天办理工卡", 0.9f),
            hit("员工手册", "考勤", "九点上班", 0.8f));
        PromptAssembler.AssembleResult result = assembler.assemble(
            "系统词", hits, List.of(turn("问题1", "回答1")), "新问题", 10, 100000);
        assertTrue(result.contextBlock().contains("【参考资料】"));
        assertTrue(result.contextBlock().contains("[1]（员工手册 > 入职>流程）"));
        assertTrue(result.contextBlock().contains("第一天办理工卡"));
        assertTrue(result.contextBlock().contains("[2]（员工手册 > 考勤）"));
        assertEquals(2, result.keptHits());
        assertEquals(1, result.keptTurns());
    }

    @Test
    void emptyHitsProduceNoDataNotice() {
        PromptAssembler.AssembleResult result = assembler.assemble(
            "系统词", List.of(), List.of(), "新问题", 10, 100000);
        assertTrue(result.contextBlock().contains("（无相关资料）"));
    }

    @Test
    void budgetTrimsOldestTurnsBeforeHits() {
        // 预算很小：先丢最旧历史轮，再丢低分检索条
        List<RetrievalHit> hits = List.of(
            hit("高分", "p", "内容内容内容内容内容", 0.95f),
            hit("低分", "p", "内容内容内容内容内容", 0.50f));
        List<ChatTurn> turns = List.of(turn("旧问题很长很长很长很长", "旧回答也很长很长很长很长"),
            turn("新问题", "新回答"));
        // 手算：系统3+问题4=7；turn1=21、turn2=6；hit≈22/条
        // 预算 33：装不下 turn1(7+27=34>33)→丢最旧轮；turn2 也装不下(28+6>33)→历史清空；检索保底高分 1 条
        PromptAssembler.AssembleResult result = assembler.assemble(
            "系统词", hits, turns, "最新问题", 2, 33);
        assertEquals(0, result.keptTurns(), "预算紧张时从最旧轮开始丢弃");
        assertEquals(1, result.keptHits(), "检索条裁剪但高分条优先保留（保底 1 条）");
    }

    @Test
    void tokenEstimationCountsChineseAndEnglish() {
        // 纯中文按字符、英文按词×1.3 简估
        assertEquals(4, PromptAssembler.estimateTokens("知库平台"));
        assertEquals(3, PromptAssembler.estimateTokens("hello world"));
    }

    @Test
    void maxTurnsWindowLimitsHistory() {
        List<ChatTurn> turns = List.of(turn("q1", "a1"), turn("q2", "a2"), turn("q3", "a3"));
        PromptAssembler.AssembleResult result = assembler.assemble(
            "系统词", List.of(), turns, "新问题", 2, 100000);
        assertEquals(2, result.keptTurns(), "仅保留最近 maxTurns 轮");
    }
}
