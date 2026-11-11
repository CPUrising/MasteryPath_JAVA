package com.masterypath.domain.model;

/**
 * 知识库引用（{@code 0047}）。
 *
 * <p>题库条目按所属路径绑定的知识库派生归属；本 record 只承载「id + 展示名」，
 * 不参与 {@code question_bank.data} 序列化。
 */
public record KnowledgeBaseRef(String id, String name) {
}
