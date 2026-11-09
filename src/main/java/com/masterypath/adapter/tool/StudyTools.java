package com.masterypath.adapter.tool;

import com.masterypath.domain.model.KnowledgePoint;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.MasteryInteraction;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.domain.mode.SessionMode;
import com.masterypath.domain.policy.MasteryPolicy;
import com.masterypath.domain.projection.QuestionCardProjector;
import com.masterypath.domain.quiz.QuizSpec;
import com.masterypath.service.LearningService;
import com.masterypath.storage.PreconditionFailedException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 学习工具：出题、判分、跳过、已讲解、延后。
 *
 * <p>五个工具都是 study / review 共用（{@code ToolClassification.STUDY_REVIEW}），
 * 执行前先做模式门控。
 */
public final class StudyTools {

    /** 题目卡片在 metadata 里的键（参考实现 {@code QUESTION_CARD_KEY}）。 */
    public static final String QUESTION_CARD_KEY = "question_card";

    private StudyTools() {
    }

    // -- mastery_quiz ---------------------------------------------------------

    /** 出题并结束回合（{@code mastery_quiz}，{@code INV-02} / {@code INV-44}）。 */
    public static final class Quiz implements Tool {

        @Override
        public String name() {
            return "mastery_quiz";
        }

        @Override
        public String description() {
            return "Pose a question for a MEMORY or PROCEDURE objective. Registers the "
                    + "expected answer server-side and puts the question on the learner's card; "
                    + "this call ENDS THE TURN. Grade the answer on the next turn.";
        }

        @Override
        public Map<String, Object> parameters() {
            return ToolSchemas.of(name());
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            ToolResult refusal = ToolSupport.wrongMode(name(), ctx);
            if (refusal != null) {
                return refusal;
            }
            LearningService service = SharedTools.service(ctx);
            LearningProgress progress = service.store().load(ctx.pathId());
            if (progress == null) {
                return ToolResult.failure("no mastery path is active");
            }
            String kpId = ToolSupport.str(params.get("knowledge_point_id"));
            LearningProgress.KnowledgePointLocation location = progress.findKnowledgePoint(kpId);
            if (!location.found()) {
                return ToolResult.failure("unknown objective: " + kpId);
            }
            KnowledgePoint kp = location.knowledgePoint();

            // review 只重测已证明的目标，不能考新的。
            if (SessionMode.enforced(ctx.sessionMode()) == SessionMode.REVIEW
                    && !MasteryPolicy.isMastered(progress, kp)) {
                return ToolResult.failure(ToolSupport.repr(kp.getName())
                        + " is not mastered yet; review re-tests proven "
                        + "objectives. Switch to study to teach it.");
            }

            String question = ToolSupport.stripped(params.get("question"));
            if (question.isEmpty()) {
                return ToolResult.failure("mastery_quiz needs question");
            }

            // 单槽位：重放已有卡片，忽略新参数（INV-02）。
            MasteryInteraction existing = service.store().getOpenInteraction(ctx.pathId());
            if (existing != null) {
                PendingQuestion open = existing.getQuestion();
                Map<String, Object> card = questionCardFor(
                        progress, open, kp.getName(), ctx.turnId());
                ctx.endTurn();
                return ToolResult.ok(
                        "No new question: an open question already exists; re-presenting it.",
                        StatePayload.map(
                                "status", "already_pending",
                                QUESTION_CARD_KEY, card));
            }

            QuizSpec.Normalized spec;
            try {
                spec = QuizSpec.normalize(params);
            } catch (QuizSpec.QuizException e) {
                return ToolResult.failure(String.valueOf(e.getMessage()));
            }
            List<String> required = QuizSpec.openRequiredKeywords(
                    spec.questionType(), spec.requiredKeywords(), spec.expectedAnswer());
            List<String> optional = "open".equals(spec.questionType())
                    ? spec.optionalKeywords() : List.of();
            List<String> accepted = ToolSupport.stringList(params.get("accepted_answers"));

            if ("short".equals(spec.questionType()) && accepted.isEmpty()
                    && !QuizSpec.isSelfGradingShort(spec.expectedAnswer())) {
                return ToolResult.failure(
                        "short questions need accepted_answers so equivalent wording is not marked "
                                + "wrong; add at least one accepted answer, or use question_type=open "
                                + "for keyword-based grading.");
            }

            PendingQuestion pending = QuizSpec.toPending(
                    new QuizSpec.Normalized(spec.questionType(), spec.options(),
                            spec.expectedAnswer(), required, optional),
                    UUID.randomUUID().toString().replace("-", ""),
                    kp.getId(),
                    location.module() == null ? "" : location.module().getId(),
                    question,
                    ToolSupport.stripped(params.get("explanation")),
                    QuizSpec.DIFFICULTIES.contains(ToolSupport.str(params.get("difficulty")))
                            ? ToolSupport.str(params.get("difficulty")) : "",
                    accepted);

            MasteryInteraction interaction = service.registerQuestion(
                    ctx.pathId(), pending, ctx.sessionId(), ctx.turnId());
            if (interaction == null) {
                // 竞态：别人先注册了，重放那一张。
                MasteryInteraction raced = service.store().getOpenInteraction(ctx.pathId());
                if (raced != null) {
                    pending = raced.getQuestion();
                    interaction = raced;
                }
            }
            PendingQuestion shown = interaction == null ? pending : interaction.getQuestion();
            Map<String, Object> card = questionCardFor(
                    progress, shown, kp.getName(), ctx.turnId());
            StringBuilder notice = new StringBuilder(
                    "The question is on the learner's card and this turn ends here; grade "
                            + "their answer next turn with mastery_grade.");
            if (MasteryPolicy.QUALITATIVE_TYPES.contains(kp.getType())) {
                notice.append(" This objective is gated qualitatively; grading will not open it.");
            }
            ctx.endTurn();
            return ToolResult.ok(notice.toString(), StatePayload.map(
                    "status", "registered",
                    "question_id", shown.getQuestionId(),
                    QUESTION_CARD_KEY, card));
        }
    }

    // -- mastery_grade --------------------------------------------------------

    /** 判分（{@code mastery_grade}）。 */
    public static final class Grade implements Tool {

        @Override
        public String name() {
            return "mastery_grade";
        }

        @Override
        public String description() {
            return "Grade the learner's answer to the open question (deterministic).";
        }

        @Override
        public Map<String, Object> parameters() {
            return ToolSchemas.of(name());
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            ToolResult refusal = ToolSupport.wrongMode(name(), ctx);
            if (refusal != null) {
                return refusal;
            }
            LearningService service = SharedTools.service(ctx);
            Map<String, Object> result;
            try {
                result = new LinkedHashMap<>(service.grade(
                        ctx.pathId(),
                        ToolSupport.str(params.get("question_id")),
                        ToolSupport.str(params.get("answer")),
                        ToolSupport.intOf(params.get("hints_used")),
                        SessionMode.enforced(ctx.sessionMode()) == SessionMode.REVIEW,
                        ctx.sessionId(),
                        ctx.turnId()));
            } catch (PreconditionFailedException e) {
                return ToolResult.failure(String.valueOf(e.getMessage()));
            }
            // 判分结果回指题目：前端据此把已作答的卡片标记为不可再答。
            result.putIfAbsent("question_id", ToolSupport.str(params.get("question_id")));
            if ("ungraded".equals(result.get("status"))) {
                return new ToolResult(false,
                        "Not graded: " + result.get("reason") + ". Ask the learner to clarify.",
                        result);
            }
            return ToolResult.ok("Graded: " + result.get("result") + ".", result);
        }
    }

    // -- mastery_skip_question ------------------------------------------------

    /** 放弃当前题目（{@code mastery_skip_question}）。 */
    public static final class SkipQuestion implements Tool {

        @Override
        public String name() {
            return "mastery_skip_question";
        }

        @Override
        public String description() {
            return "Abandon the open question and clear it from the learner's card.";
        }

        @Override
        public Map<String, Object> parameters() {
            return ToolSchemas.of(name());
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            ToolResult refusal = ToolSupport.wrongMode(name(), ctx);
            if (refusal != null) {
                return refusal;
            }
            try {
                Map<String, Object> result = SharedTools.service(ctx).skipQuestion(
                        ctx.pathId(), ToolSupport.str(params.get("question_id")));
                return ToolResult.ok("Question skipped.", result);
            } catch (PreconditionFailedException e) {
                return ToolResult.failure(String.valueOf(e.getMessage()));
            }
        }
    }

    // -- mastery_note_explained -----------------------------------------------

    /** 记录已讲解（{@code mastery_note_explained}）。 */
    public static final class NoteExplained implements Tool {

        @Override
        public String name() {
            return "mastery_note_explained";
        }

        @Override
        public String description() {
            return "Record what was taught. Does not assess or grant mastery.";
        }

        @Override
        public Map<String, Object> parameters() {
            return ToolSchemas.of(name());
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            ToolResult refusal = ToolSupport.wrongMode(name(), ctx);
            if (refusal != null) {
                return refusal;
            }
            String summary = ToolSupport.stripped(params.get("summary"));
            if (summary.isEmpty()) {
                return ToolResult.failure("a teaching summary is required");
            }
            try {
                Map<String, Object> result = SharedTools.service(ctx).noteExplained(
                        ctx.pathId(), ToolSupport.str(params.get("knowledge_point_id")), summary);
                return ToolResult.ok("Recorded explanation (no mastery credit).", result);
            } catch (PreconditionFailedException e) {
                return ToolResult.failure(String.valueOf(e.getMessage()));
            }
        }
    }

    // -- mastery_defer_objective ----------------------------------------------

    /** 延后目标（{@code mastery_defer_objective}）。 */
    public static final class DeferObjective implements Tool {

        @Override
        public String name() {
            return "mastery_defer_objective";
        }

        @Override
        public String description() {
            return "Set a knowledge point aside for now without claiming mastery.";
        }

        @Override
        public Map<String, Object> parameters() {
            return ToolSchemas.of(name());
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            ToolResult refusal = ToolSupport.wrongMode(name(), ctx);
            if (refusal != null) {
                return refusal;
            }
            try {
                Map<String, Object> result = SharedTools.service(ctx).deferObjective(
                        ctx.pathId(),
                        ToolSupport.str(params.get("knowledge_point_id")),
                        ToolSupport.str(params.get("note")));
                return ToolResult.ok("Objective deferred.", result);
            } catch (PreconditionFailedException e) {
                return ToolResult.failure(String.valueOf(e.getMessage()));
            }
        }
    }

    // -- 辅助 -----------------------------------------------------------------

    /** 按题目自身的知识点构造卡片；找不到时退回调用方给的目标名。 */
    static Map<String, Object> questionCardFor(LearningProgress progress, PendingQuestion pending,
                                               String fallbackObjectiveName, String turnId) {
        String kpId = pending.getKnowledgePointId() == null ? "" : pending.getKnowledgePointId();
        var location = progress.findKnowledgePoint(kpId);
        String name = location.found() ? location.knowledgePoint().getName() : fallbackObjectiveName;
        return questionCard(pending, kpId, name,
                attemptNumber(progress, kpId), turnId);
    }

    /** 构造题目卡片：公共投影 + 目标名 + 第几次尝试 + 回合上下文（{@code INV-07}）。 */
    static Map<String, Object> questionCard(PendingQuestion pending, String knowledgePointId,
                                            String objectiveName, int attempt, String turnId) {
        Map<String, Object> card = new LinkedHashMap<>(
                QuestionCardProjector.publicPendingQuestion(pending));
        card.put("card_kind", "mastery");
        card.put("turn_id", turnId == null ? "" : turnId);
        card.put("objective", Map.of(
                "id", knowledgePointId == null ? "" : knowledgePointId,
                "name", objectiveName == null ? "" : objectiveName));
        card.put("objective_name", objectiveName);
        card.put("attempt", attempt);
        return card;
    }

    /** 该知识点已尝试次数 + 1（参考实现 {@code attempt_number}）。 */
    static int attemptNumber(LearningProgress progress, String kpId) {
        int count = 0;
        for (var attempt : progress.getQuizAttempts()) {
            if (kpId.equals(attempt.getKnowledgePointId())) {
                count++;
            }
        }
        return count + 1;
    }

    /** 空列表（避免重复字面量）。 */
    static List<String> noKeywords() {
        return new ArrayList<>();
    }
}
