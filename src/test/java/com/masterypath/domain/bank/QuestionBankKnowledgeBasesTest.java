package com.masterypath.domain.bank;

import com.masterypath.domain.model.KnowledgeBaseRef;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.TopicSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 题库的知识库归属解析（{@code 0047}）。 */
class QuestionBankKnowledgeBasesTest {

    @Test
    @DisplayName("只解析 kind=knowledge_base 的来源，file 忽略")
    void onlyKnowledgeBaseSources() {
        LearningProgress progress = progress(
                source("s1", "knowledge_base", "kb1", "KB One"),
                source("s2", "file", "f1", "notes.md"),
                source("s3", "knowledge_base", "kb2", "KB Two"));

        assertThat(QuestionBankKnowledgeBases.of(progress))
                .containsExactly(
                        new KnowledgeBaseRef("kb1", "KB One"),
                        new KnowledgeBaseRef("kb2", "KB Two"));
    }

    @Test
    @DisplayName("sourceId 为空回退 id，label 为空回退 id")
    void fallsBackToId() {
        LearningProgress progress = progress(
                source("s1", "knowledge_base", "", ""),
                source("s2", "knowledge_base", "kb2", ""));

        assertThat(QuestionBankKnowledgeBases.of(progress))
                .containsExactly(
                        new KnowledgeBaseRef("s1", "s1"),
                        new KnowledgeBaseRef("kb2", "kb2"));
    }

    @Test
    @DisplayName("同一知识库重复绑定只保留一次")
    void deduplicatesById() {
        LearningProgress progress = progress(
                source("s1", "knowledge_base", "kb1", "First"),
                source("s2", "knowledge_base", "kb1", "Second"));

        assertThat(QuestionBankKnowledgeBases.of(progress))
                .containsExactly(new KnowledgeBaseRef("kb1", "First"));
    }

    @Test
    @DisplayName("路径不存在或无来源时返回空列表")
    void emptyWhenMissing() {
        assertThat(QuestionBankKnowledgeBases.of(null)).isEmpty();
        assertThat(QuestionBankKnowledgeBases.of(progress())).isEmpty();
    }

    @Test
    @DisplayName("筛选为空放行，非空按 OR 命中")
    void matchesOr() {
        List<KnowledgeBaseRef> refs = List.of(
                new KnowledgeBaseRef("kb1", "KB One"),
                new KnowledgeBaseRef("kb2", "KB Two"));

        assertThat(QuestionBankKnowledgeBases.matches(refs, null)).isTrue();
        assertThat(QuestionBankKnowledgeBases.matches(refs, List.of())).isTrue();
        assertThat(QuestionBankKnowledgeBases.matches(refs, List.of("kb2"))).isTrue();
        assertThat(QuestionBankKnowledgeBases.matches(refs, List.of("kb9", "kb1"))).isTrue();
        assertThat(QuestionBankKnowledgeBases.matches(refs, List.of("kb9"))).isFalse();
    }

    @Test
    @DisplayName("未关联条目只在无筛选时可见")
    void unassignedOnlyWithoutFilter() {
        assertThat(QuestionBankKnowledgeBases.matches(List.of(), List.of())).isTrue();
        assertThat(QuestionBankKnowledgeBases.matches(List.of(), List.of("kb1"))).isFalse();
    }

    private static LearningProgress progress(TopicSource... sources) {
        LearningProgress progress = new LearningProgress("p");
        progress.setSources(List.of(sources));
        return progress;
    }

    private static TopicSource source(String id, String kind, String sourceId, String label) {
        TopicSource source = new TopicSource();
        source.setId(id);
        source.setKind(kind);
        source.setSourceId(sourceId);
        source.setLabel(label);
        return source;
    }
}
