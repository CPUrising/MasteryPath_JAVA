package com.masterypath.adapter.tool;

import com.masterypath.domain.grading.Decision;
import com.masterypath.domain.grading.Grading;
import com.masterypath.domain.model.KnowledgePoint;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.MasteryInteraction;
import com.masterypath.domain.model.PendingOption;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.domain.mode.SessionMode;
import com.masterypath.domain.policy.MasteryPolicy;
import com.masterypath.domain.projection.QuestionCardProjector;
import com.masterypath.domain.quiz.QuizSpec;
import com.masterypath.service.LearningService;
import com.masterypath.storage.PreconditionFailedException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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

    /** 判分器对超过 30 字的简答只做精确匹配；注册时按同一把尺子校验。 */
    private static final int SHORT_ANSWER_LIMIT = 30;

    /** 长简答且在注册期无法判定的拒绝文案。 */
    private static final String SHORT_ANSWER_TOO_LONG =
            "short answers are graded by exact/fuzzy match and an expected answer longer than "
                    + SHORT_ANSWER_LIMIT + " characters never matches; use question_type=open with "
                    + "required_keywords/optional_keywords, or add an accepted_answers entry of "
                    + SHORT_ANSWER_LIMIT + " characters or fewer.";

    /** 定量目标不接受 open 的拒绝文案：open 判对也只记 partial，永远拿不到掌握度。 */
    private static final String OPEN_ON_QUANTITATIVE =
            "open questions cannot earn mastery credit for a memory or procedure objective: a "
                    + "correct reply is recorded as partial and never raises the score. Use "
                    + "question_type=choice, or question_type=short with an expected answer of "
                    + SHORT_ANSWER_LIMIT + " characters or fewer (add accepted_answers for "
                    + "equivalent phrasings). Open questions are for concept/design objectives, "
                    + "which are opened by mastery_assess.";

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
                    + "this call ENDS THE TURN. Match the type to the objective: memory/procedure "
                    + "accept choice or short only — an open answer is recorded as partial even "
                    + "when correct and never raises the score. Use question_type=short only when "
                    + "the expected answer is 30 characters or fewer; for explanations use "
                    + "mastery_assess on a concept/design objective.";
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
            // 定量门槛只认 correct：open 判对也被降为 partial，这道题永远赢不了。
            if ("open".equals(spec.questionType())
                    && "quantitative".equals(MasteryPolicy.gateKind(kp))) {
                return ToolResult.failure(OPEN_ON_QUANTITATIVE);
            }
            if ("choice".equals(spec.questionType())) {
                spec = shuffleChoice(spec, new java.util.Random());
            }
            List<String> required = QuizSpec.openRequiredKeywords(
                    spec.questionType(), spec.requiredKeywords(), spec.expectedAnswer());
            List<String> optional = "open".equals(spec.questionType())
                    ? spec.optionalKeywords() : List.of();
            List<String> accepted = ToolSupport.stringList(params.get("accepted_answers"));

            if (shortAnswerTooLong(spec, accepted)) {
                return ToolResult.failure(SHORT_ANSWER_TOO_LONG);
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
            String questionId = ToolSupport.str(params.get("question_id")).strip();
            if (questionId.isEmpty()) {
                MasteryInteraction open = service.store().getOpenInteraction(ctx.pathId());
                if (open == null || open.getQuestion() == null) {
                    return ToolResult.failure("no open question to grade");
                }
                questionId = open.getQuestion().getQuestionId();
            }
            MasteryInteraction interaction = service.store().getInteraction(ctx.pathId(), questionId);
            String answer = ToolSupport.str(params.get("answer"));
            int hintsUsed = ToolSupport.intOf(params.get("hints_used"));
            Map<String, Object> result;
            try {
                result = new LinkedHashMap<>(service.grade(
                        ctx.pathId(),
                        questionId,
                        answer,
                        hintsUsed,
                        SessionMode.enforced(ctx.sessionMode()) == SessionMode.REVIEW,
                        ctx.sessionId(),
                        ctx.turnId()));
            } catch (PreconditionFailedException e) {
                return ToolResult.failure(String.valueOf(e.getMessage()));
            }
            // 无法解析的作答不判、不落状态（INV-46）：让导师回问澄清，绝不判错。
            if (Decision.UNGRADED.equals(ToolSupport.str(result.get("status")))) {
                return ToolResult.failure("Not graded: " + ToolSupport.str(result.get("reason"))
                        + ". Ask the learner to clarify.", result);
            }
            // 判分结果回指题目，并附带卡片复盘所需的答案键（提交后才释放）。
            result.putIfAbsent("question_id", questionId);
            result.putIfAbsent("learner_answer", answer);
            result.putIfAbsent("independent", hintsUsed == 0);
            if (interaction != null && interaction.getQuestion() != null) {
                PendingQuestion question = interaction.getQuestion();
                String correctLabel = question.getExpectedAnswer();
                String correctBody = "";
                for (var option : question.getOptions()) {
                    if (option.getLabel().equalsIgnoreCase(correctLabel)) {
                        correctBody = option.getBody();
                        break;
                    }
                }
                result.putIfAbsent("correct_label", correctLabel);
                result.putIfAbsent("correct_body", correctBody);
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
                LearningService service = SharedTools.service(ctx);
                String questionId = ToolSupport.str(params.get("question_id")).strip();
                if (questionId.isEmpty()) {
                    MasteryInteraction open = service.store().getOpenInteraction(ctx.pathId());
                    if (open == null || open.getQuestion() == null) {
                        return ToolResult.failure("no open question to skip");
                    }
                    questionId = open.getQuestion().getQuestionId();
                }
                Map<String, Object> result = service.skipQuestion(
                        ctx.pathId(), questionId, ctx.sessionId(), ctx.turnId());
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
                        ctx.pathId(), ToolSupport.str(params.get("knowledge_point_id")), summary,
                        ctx.sessionId(), ctx.turnId());
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

    /**
     * 简答题长度契约：期望答案超过判分器的模糊匹配上限时，必须有同样短的等价答案。
     *
     * <p>归一化与 {@code Grading.normalize} 完全一致，保证「判分多长」与「校验多长」同一把尺子。
     */
    static boolean shortAnswerTooLong(QuizSpec.Normalized spec, List<String> accepted) {
        if (!"short".equals(spec.questionType())) {
            return false;
        }
        if (Grading.normalize(spec.expectedAnswer()).length() <= SHORT_ANSWER_LIMIT) {
            return false;
        }
        for (String candidate : accepted) {
            if (!candidate.isBlank()
                    && Grading.normalize(candidate).length() <= SHORT_ANSWER_LIMIT) {
                return false;
            }
        }
        return true;
    }

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
        card.put("allow_free_text", true);
        return card;
    }

    /**
     * 打乱选择题选项并按位置重贴标签（复刻 DeepTutor {@code _shuffle_choice_options}）。
     *
     * <p>模型经常把正确项放在第一个；乱序后标签连续，{@code expected_answer} 跟随正确正文。
     */
    static QuizSpec.Normalized shuffleChoice(QuizSpec.Normalized spec, Random rng) {
        List<PendingOption> options = new ArrayList<>(spec.options());
        if (options.size() < 2) {
            return spec;
        }
        String expectedBody = "";
        for (PendingOption option : options) {
            if (option.getLabel().equalsIgnoreCase(spec.expectedAnswer())) {
                expectedBody = option.getBody();
                break;
            }
        }
        if (expectedBody.isEmpty()) {
            return spec;
        }
        Collections.shuffle(options, rng);
        List<PendingOption> relabeled = new ArrayList<>();
        String newExpected = spec.expectedAnswer();
        for (int index = 0; index < options.size(); index++) {
            String label = String.valueOf((char) ('A' + index));
            PendingOption option = options.get(index);
            relabeled.add(new PendingOption(label, option.getBody()));
            if (option.getBody().equals(expectedBody)) {
                newExpected = label;
            }
        }
        return new QuizSpec.Normalized(spec.questionType(), relabeled, newExpected,
                spec.requiredKeywords(), spec.optionalKeywords());
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
