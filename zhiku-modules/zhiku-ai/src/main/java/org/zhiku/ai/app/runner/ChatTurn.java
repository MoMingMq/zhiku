package org.zhiku.ai.app.runner;

/**
 * 多轮历史的一轮问答（user/assistant 成对）
 *
 * @author zhiku
 */
public record ChatTurn(String question, String answer) {
}
