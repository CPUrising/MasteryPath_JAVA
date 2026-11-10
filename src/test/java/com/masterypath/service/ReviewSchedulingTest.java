package com.masterypath.service;

import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.domain.model.RepetitionState;
import com.masterypath.domain.model.ReviewTask;
import com.masterypath.domain.policy.MasteryPolicy;
import com.masterypath.domain.schedule.Sm2Scheduler;
import com.masterypath.storage.LearningStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 调度接线（{@code TC-G2}…{@code TC-G5} 的服务层侧、{@code TC-G6}、{@code TC-G7}）。
 *
 * <p>对照标准：{@code ../MasteryPath/tests/test_review.py}。
 * 本类补上参考实现没有的三块证据：三个入口的事件<b>顺序</b>、分支 3（会话内只 touch）的集成用例、
 * {@code assess} 未通过不播种。
 */
class ReviewSchedulingTest {

    private static final double EPS = 1e-6;

    @TempDir
    Path tempDir;

    private LearningStore store;
    private LearningService service;

    @BeforeEach
    void setUp() {
        store = new LearningStore(tempDir);
        service = new LearningService(store);
        store.create(ServiceFixtures.onePoint("p", KnowledgeType.MEMORY));
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    // -- 分支 1：掌握即播种 ---------------------------------------------------

    @Test
    @DisplayName("判分首次掌握：写入状态与队列、发 review.scheduled，事件顺序与参考实现一致")
    void gradeSeedsAndEmitsInOrder() {
        register("q0", "4");
        service.grade("p", "q0", "4", 0, false, "", "");
        register("q1", "4");
        service.grade("p", "q1", "4", 0, false, "", "");
        register("q2", "4");
        int before = store.eventsSince("p", 0).size();

        service.grade("p", "q2", "4", 0, false, "", "");

        LearningProgress progress = store.load("p");
        assertThat(progress.getRepetitionStates()).containsKey("k1");
        assertThat(progress.getReviewQueue()).hasSize(1);
        assertThat(progress.getReviewQueue().get(0).getKnowledgePointId()).isEqualTo("k1");
        assertThat(progress.getRepetitionStates().get("k1").getProvenance()).isEqualTo("system");
        // 只比较最后一次判分产生的事件：前两次判分不会清门槛。
        assertThat(eventTypes().subList(before, eventTypes().size()))
                .containsExactly("review.scheduled", "attempt.graded", "objective.mastered");
    }

    @Test
    @DisplayName("播种事件的 payload 含 provenance，不含 scheduled_after_failure")
    void seedPayloadShape() {
        master();

        String payload = payloadOf("review.scheduled");
        assertThat(payload).contains("\"knowledge_point_id\":\"k1\"")
                .contains("\"interval_index\":1")
                .contains("\"provenance\":\"system\"")
                .doesNotContain("scheduled_after_failure");
    }

    @Test
    @DisplayName("学习者声明掌握：provenance=learner，首间隔约为系统轨的一半")
    void overrideSeedsLearnerTrack() {
        service.overrideMastery("p", "k1", "I know this");

        RepetitionState state = store.load("p").getRepetitionStates().get("k1");
        assertThat(state.getProvenance()).isEqualTo("learner");
        double firstDays = (state.getNextReviewAt() - state.getLastScheduledAt())
                / Sm2Scheduler.SECONDS_PER_DAY;
        assertThat(firstDays).isCloseTo(Sm2Scheduler.LEARNER_FIRST_FACTOR, within(EPS));
        assertThat(eventTypes()).containsExactly("review.scheduled", "mastery.override");
    }

    @Test
    @DisplayName("定性评估通过：播种并发 review.scheduled，事件顺序在 qualitative.assessed 之前")
    void assessSeedsBeforeAssessedEvent() {
        store.create(ServiceFixtures.onePoint("c", KnowledgeType.CONCEPT));

        service.assess("c", "k1", true, "own words", null, false, "", "");

        assertThat(store.load("c").getRepetitionStates()).containsKey("k1");
        assertThat(eventTypes("c")).containsExactly(
                "review.scheduled", "qualitative.assessed", "objective.mastered");
    }

    @Test
    @DisplayName("定性评估未通过：不播种、不发 review.scheduled")
    void failedAssessSeedsNothing() {
        store.create(ServiceFixtures.onePoint("c", KnowledgeType.CONCEPT));

        service.assess("c", "k1", false, "vague", null, false, "", "");

        assertThat(store.load("c").getRepetitionStates()).isEmpty();
        assertThat(store.load("c").getReviewQueue()).isEmpty();
        assertThat(eventTypes("c")).doesNotContain("review.scheduled");
    }

    // -- 分支 2：正式复习重排 -------------------------------------------------

    @Test
    @DisplayName("TC-G3：review 模式下答对，间隔序号前进且标志清零")
    void reviewCorrectReschedulesForward() {
        master();
        makeDue();
        register("q2", "4");

        service.grade("p", "q2", "4", 0, true, "", "");

        RepetitionState state = store.load("p").getRepetitionStates().get("k1");
        assertThat(state.getIntervalIndex()).isGreaterThanOrEqualTo(2);
        assertThat(state.isScheduledAfterFailure()).isFalse();
        assertThat(state.getReviewCount()).isEqualTo(1);
        assertThat(state.getNextReviewAt()).isGreaterThan(0.0);
        assertThat(lastPayloadOf("review.scheduled"))
                .contains("\"scheduled_after_failure\":false")
                .doesNotContain("\"provenance\"");
    }

    @Test
    @DisplayName("TC-G2：review 模式下答错，进入重学步并标记锚点由失败产生")
    void reviewFailureSetsFlag() {
        master();
        makeDue();
        register("q2", "4");

        service.grade("p", "q2", "9", 0, true, "", "");

        RepetitionState state = store.load("p").getRepetitionStates().get("k1");
        assertThat(state.isScheduledAfterFailure()).isTrue();
        assertThat(state.getLapseCount()).isEqualTo(1);
        assertThat(state.getIntervalIndex()).isZero();
        assertThat(lastPayloadOf("review.scheduled"))
                .contains("\"scheduled_after_failure\":true");
    }

    @Test
    @DisplayName("TC-G3：失败后下一次正式复习成功，标志清零")
    void failureThenSuccessClearsFlag() {
        master();
        makeDue();
        register("q2", "4");
        service.grade("p", "q2", "9", 0, true, "", "");
        makeDue();
        register("q3", "4");

        service.grade("p", "q3", "4", 0, true, "", "");

        RepetitionState state = store.load("p").getRepetitionStates().get("k1");
        assertThat(state.isScheduledAfterFailure()).isFalse();
        assertThat(state.getLapseCount()).isEqualTo(1);
        assertThat(state.getIntervalIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("review 模式但从未掌握：既不播种也不重排")
    void reviewWithoutStateDoesNothing() {
        register("q1", "4");

        service.grade("p", "q1", "9", 0, true, "", "");

        assertThat(store.load("p").getRepetitionStates()).isEmpty();
        assertThat(eventTypes()).doesNotContain("review.scheduled");
    }

    // -- 分支 3：会话内只 touch ----------------------------------------------

    @Test
    @DisplayName("TC-G4：已掌握目标上再练一次，只改 last_review_at，锚点与队列不变")
    void inSessionPracticeKeepsAnchor() {
        master();
        RepetitionState before = store.load("p").getRepetitionStates().get("k1");
        double anchor = before.getNextReviewAt();
        register("q2", "4");
        int eventsBefore = store.eventsSince("p", 0).size();

        service.grade("p", "q2", "4", 0, false, "", "");

        RepetitionState after = store.load("p").getRepetitionStates().get("k1");
        assertThat(after.getNextReviewAt()).isEqualTo(anchor);
        assertThat(after.isScheduledAfterFailure()).isFalse();
        assertThat(after.getReviewCount()).isZero();
        assertThat(after.getLastReviewAt()).isNotNull();
        assertThat(store.load("p").getReviewQueue()).hasSize(1);
        // 会话内练习只应产生 attempt.graded 一条事件：不 upsert、不发 review.scheduled。
        assertThat(eventTypes().subList(eventsBefore, eventTypes().size()))
                .containsExactly("attempt.graded");
    }

    // -- 复习队列进入 nextObjective ------------------------------------------

    @Test
    @DisplayName("到期后 nextObjective 返回 review（复习队列不再是死代码）")
    void dueReviewBecomesNextAction() {
        master();
        makeDue();

        assertThat(MasteryPolicy.nextObjective(store.load("p")).action()).isEqualTo("review");
        assertThat(MasteryPolicy.nextObjective(store.load("p")).knowledgePointId()).isEqualTo("k1");
    }

    @Test
    @DisplayName("未到期时 nextObjective 不返回 review")
    void notDueYetNoReview() {
        master();

        assertThat(MasteryPolicy.nextObjective(store.load("p")).action()).isEqualTo("complete");
    }

    // -- TC-G6 内部量不暴露 ---------------------------------------------------

    @Test
    @DisplayName("TC-G6：服务层公开方法签名不含调度内部类型（部分覆盖，准入拒绝属 0013/0014）")
    void schedulerInternalsNotExposed() {
        for (Method method : LearningService.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            assertThat(method.getParameterTypes())
                    .as("公开方法 %s 不得暴露调度内部量", method.getName())
                    .doesNotContain(RepetitionState.class, ReviewTask.class);
            assertThat(method.getReturnType())
                    .as("公开方法 %s 不得返回调度内部量", method.getName())
                    .isNotIn(RepetitionState.class, ReviewTask.class);
        }
    }

    // -- 辅助 ----------------------------------------------------------------

    /** 用判分把 k1 推到掌握（会顺带播种）。 */
    private void master() {
        for (int index = 0; index < 3; index++) {
            register("m" + index, "4");
            service.grade("p", "m" + index, "4", 0, false, "", "");
        }
    }

    private void register(String questionId, String expected) {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId(questionId);
        pending.setKnowledgePointId("k1");
        pending.setModuleId("m1");
        pending.setPrompt("2+2?");
        pending.setQuestionType(PendingQuestion.QuestionType.SHORT);
        pending.setExpectedAnswer(expected);
        service.registerQuestion("p", pending, "", "");
    }

    /** 把状态与队列都推到已到期，模拟「隔天再复习」。 */
    private void makeDue() {
        store.mutate("p", tx -> {
            tx.progress().getRepetitionStates().get("k1").setNextReviewAt(0.0);
            for (ReviewTask task : tx.progress().getReviewQueue()) {
                task.setDueAt(0.0);
            }
            return tx.progress();
        });
    }

    private List<String> eventTypes() {
        return eventTypes("p");
    }

    private List<String> eventTypes(String pathId) {
        return store.eventsSince(pathId, 0).stream()
                .map(event -> String.valueOf(event.get("event_type")))
                .toList();
    }

    private String payloadOf(String eventType) {
        return store.eventsSince("p", 0).stream()
                .filter(event -> eventType.equals(event.get("event_type")))
                .map(event -> String.valueOf(event.get("payload")))
                .findFirst()
                .orElseThrow();
    }

    /** 同名事件可能发生多次（播种一次、每次复习一次），断言取最后一次。 */
    private String lastPayloadOf(String eventType) {
        List<String> payloads = store.eventsSince("p", 0).stream()
                .filter(event -> eventType.equals(event.get("event_type")))
                .map(event -> String.valueOf(event.get("payload")))
                .toList();
        return payloads.get(payloads.size() - 1);
    }
}
