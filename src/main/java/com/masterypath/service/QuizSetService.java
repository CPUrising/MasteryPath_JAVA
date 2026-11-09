package com.masterypath.service;

import com.masterypath.domain.grading.Decision;
import com.masterypath.domain.model.QuizItem;
import com.masterypath.domain.model.QuizSet;
import com.masterypath.domain.model.TimeSupport;
import com.masterypath.domain.projection.QuizSetProjector;
import com.masterypath.domain.quizset.QuizSetGrader;
import com.masterypath.storage.LearningStore;
import com.masterypath.storage.PreconditionFailedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 生成型练习题卷的服务编排（{@code 0033}）。
 *
 * <p>只读写 {@code quiz_sets} / {@code quiz_items}；不触碰掌握聚合、证据、待答题槽位。
 */
public class QuizSetService {

    private final LearningStore store;

    public QuizSetService(LearningStore store) {
        this.store = store;
    }

    /** 保存整套题（生成时）或提交结果。 */
    public QuizSet save(QuizSet set, List<QuizItem> items) {
        store.saveQuizSet(set, items);
        return set;
    }

    /** 题卷元数据；不存在返回 {@code null}。 */
    public QuizSet get(String setId) {
        return store.getQuizSet(setId);
    }

    /** 题卷题目；不存在返回空列表。 */
    public List<QuizItem> items(String setId) {
        return store.listQuizItems(setId);
    }

    /** 公开投影；题卷不存在或不属于该路径时返回 {@code null}。 */
    public Map<String, Object> view(String pathId, String setId, boolean includeAnswers) {
        QuizSet set = store.getQuizSet(setId);
        if (set == null || !set.getPathId().equals(pathId)) {
            return null;
        }
        return QuizSetProjector.publicSet(set, store.listQuizItems(setId), includeAnswers);
    }

    /**
     * 整卷提交。
     *
     * <p>全部题目可判定才落库；任一题 {@code ungraded} 时返回未判定且零状态变更。
     * 已提交的题卷直接返回首次结果（幂等）。
     */
    public SubmitOutcome submit(String pathId, String setId, Map<String, String> answers) {
        QuizSet set = store.getQuizSet(setId);
        if (set == null || !set.getPathId().equals(pathId)) {
            throw new PreconditionFailedException("unknown quiz set: " + setId);
        }
        List<QuizItem> items = store.listQuizItems(setId);
        if (QuizSet.STATUS_SUBMITTED.equals(set.getStatus())) {
            return new SubmitOutcome(QuizSet.STATUS_SUBMITTED, set, items, List.of());
        }

        List<String> ungradedReasons = new ArrayList<>();
        int correct = 0;
        for (QuizItem item : items) {
            String answer = answers == null ? "" : answers.getOrDefault(item.questionId(), "");
            Decision decision = QuizSetGrader.grade(item, answer);
            if (decision.isUngraded()) {
                ungradedReasons.add(item.questionId() + ": " + decision.reason());
                continue;
            }
            item.setUserAnswer(answer);
            item.setCorrect(Decision.CORRECT.equals(decision.result()));
            item.setResult(decision.result());
            item.setUpdatedAt(TimeSupport.nowSeconds());
            if (item.isCorrect()) {
                correct++;
            }
        }
        if (!ungradedReasons.isEmpty()) {
            // 零状态变更：不写回任何逐题结果。
            return new SubmitOutcome("ungraded", set, items, ungradedReasons);
        }

        set.setStatus(QuizSet.STATUS_SUBMITTED);
        set.setScore(items.isEmpty() ? 0.0 : (double) correct / items.size());
        set.setSubmittedAt(TimeSupport.nowSeconds());
        store.saveQuizSet(set, items);
        return new SubmitOutcome(QuizSet.STATUS_SUBMITTED, set, items, List.of());
    }

    /** 提交结果：{@code status} 为 {@code submitted} 或 {@code ungraded}。 */
    public record SubmitOutcome(String status, QuizSet set, List<QuizItem> items,
                                List<String> ungradedReasons) {
    }
}
