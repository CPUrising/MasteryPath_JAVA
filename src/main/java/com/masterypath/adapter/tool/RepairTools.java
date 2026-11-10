package com.masterypath.adapter.tool;

import com.masterypath.domain.model.KnowledgePoint;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.mode.SessionMode;
import com.masterypath.domain.policy.MasteryPolicy;
import com.masterypath.service.LearningService;
import com.masterypath.storage.PreconditionFailedException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 修复、画像、评估、捕获工具。
 *
 * <p>这一组是「服务层异常 → {@code ToolResult(ok=false)}」转换最集中的地方：
 * 参考实现 `test_repair_tool_requires_reason_and_valid_action` 与
 * `test_profile_tool_records_partial_fields` 断言的正是这个转换。
 */
public final class RepairTools {

    /** 画像白名单（与参考实现 {@code MasteryProfileTool} 的键顺序一致）。 */
    private static final List<String> PROFILE_KEYS = List.of(
            "prior_knowledge",
            "target_level",
            "time_budget",
            "preferences",
            "teaching_strategy",
            "notes");

    private RepairTools() {
    }

    // -- mastery_repair_question ----------------------------------------------

    /** 修复题目（{@code mastery_repair_question}）。 */
    public static final class RepairQuestion implements Tool {

        @Override
        public String name() {
            return "mastery_repair_question";
        }

        @Override
        public String description() {
            return "Repair an invalid question or a wrong answer key. action='void' removes "
                    + "the attempt's mastery credit and the question from the wrong-answer set; "
                    + "action='correct' writes the true answer and re-grades the stored reply. "
                    + "Correcting an open question updates the record but never changes mastery "
                    + "credit (open is always graded partial). Requires a short reason.";
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
            Map<String, Object> result;
            try {
                result = SharedTools.service(ctx).repairQuestion(
                        ctx.pathId(),
                        ToolSupport.str(params.get("question_id")),
                        ToolSupport.str(params.get("action")),
                        ToolSupport.str(params.get("expected_answer")),
                        ToolSupport.str(params.get("reason")),
                        ctx.sessionId(),
                        ctx.turnId());
            } catch (PreconditionFailedException e) {
                return ToolResult.failure(String.valueOf(e.getMessage()));
            }
            String content;
            if ("void".equals(result.get("action"))) {
                content = "The question was voided; continue from mastery_status.next.";
            } else if (Boolean.FALSE.equals(result.get("effective"))) {
                content = "The answer key was updated, but this is an open question: a correct "
                        + "reply is still recorded as partial, so mastery credit is unchanged. "
                        + "Continue with a choice or short question.";
            } else {
                content = "The answer key was corrected; the stored reply now grades "
                        + ToolSupport.str(result.get("result")) + ".";
            }
            return ToolResult.ok(content, result);
        }
    }

    // -- mastery_profile ------------------------------------------------------

    /** 记录学习者画像（{@code mastery_profile}）。 */
    public static final class Profile implements Tool {

        @Override
        public String name() {
            return "mastery_profile";
        }

        @Override
        public String description() {
            return "Record what the learner told you about themselves for this goal: prior "
                    + "knowledge, target level, time budget, preferences, teaching strategy, "
                    + "or notes. Omitted fields keep their previous value; never invent an "
                    + "answer the learner did not give.";
        }

        @Override
        public Map<String, Object> parameters() {
            return ToolSchemas.of(name());
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            Map<String, Object> fields = new LinkedHashMap<>();
            for (String key : PROFILE_KEYS) {
                if (params.containsKey(key) && params.get(key) != null) {
                    fields.put(key, params.get(key));
                }
            }
            if (fields.isEmpty()) {
                return ToolResult.failure(
                        "mastery_profile needs at least one of prior_knowledge, "
                                + "target_level, time_budget, preferences, teaching_strategy "
                                + "or notes.");
            }
            LearningService.ProfileUpdate update;
            try {
                update = SharedTools.service(ctx).recordLearnerProfile(
                        ctx.pathId(), fields, ctx.sessionId(), ctx.turnId());
            } catch (PreconditionFailedException | IllegalArgumentException e) {
                return ToolResult.failure(String.valueOf(e.getMessage()));
            }
            return ToolResult.ok("Recorded the learner profile.", StatePayload.map(
                    "status", "recorded",
                    "updated_fields", update.changed(),
                    "profile", update.profile()));
        }
    }

    // -- mastery_assess -------------------------------------------------------

    /** 定性评估（{@code mastery_assess}）。 */
    public static final class Assess implements Tool {

        @Override
        public String name() {
            return "mastery_assess";
        }

        @Override
        public String description() {
            return "Judge a CONCEPT or DESIGN objective qualitatively from the learner's own "
                    + "explanation (Feynman-style). This is how those types are opened; a "
                    + "multiple-choice answer never opens them.";
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
            // 注意：这里的文案比服务层的短，两者是不同检查，不可合并。
            if (SessionMode.enforced(ctx.sessionMode()) == SessionMode.REVIEW
                    && !MasteryPolicy.isMastered(progress, kp)) {
                return ToolResult.failure(ToolSupport.repr(kp.getName())
                        + " is not mastered yet; review re-tests proven objectives.");
            }
            boolean passed = ToolSupport.bool(params.get("passed"));
            Double quality = params.get("quality") instanceof Number number
                    ? number.doubleValue() : null;
            Map<String, Object> result;
            try {
                result = service.assess(
                        ctx.pathId(),
                        kpId,
                        passed,
                        ToolSupport.str(params.get("evidence")),
                        quality,
                        SessionMode.enforced(ctx.sessionMode()) == SessionMode.REVIEW,
                        ctx.sessionId(),
                        ctx.turnId());
            } catch (PreconditionFailedException e) {
                return ToolResult.failure(String.valueOf(e.getMessage()));
            }
            return ToolResult.ok(
                    "Assessment recorded: " + (passed ? "passed" : "not yet") + ".", result);
        }
    }

    // -- mastery_propose_objective --------------------------------------------

    /** 记录候选目标（{@code mastery_propose_objective}，{@code INV-53/54}）。 */
    public static final class ProposeObjective implements Tool {

        @Override
        public String name() {
            return "mastery_propose_objective";
        }

        @Override
        public String description() {
            return "Record a candidate objective discovered while learning — a missing "
                    + "prerequisite, a new sub-topic. It does NOT change the map or the "
                    + "frontier; the outline reconciles it later.";
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
            String name = ToolSupport.stripped(params.get("name"));
            if (name.length() < 2) {
                return ToolResult.failure(
                        "a candidate objective needs a name of 2+ characters");
            }
            try {
                Map<String, Object> result = SharedTools.service(ctx).proposeObjective(
                        ctx.pathId(),
                        name,
                        ToolSupport.str(params.get("type")),
                        ToolSupport.str(params.get("reason")),
                        ToolSupport.str(params.get("module_id")),
                        ToolSupport.stringList(params.get("sources")),
                        "tutor",
                        ctx.sessionId(),
                        ctx.turnId());
                return ToolResult.ok("Recorded a candidate objective.", result);
            } catch (PreconditionFailedException | IllegalArgumentException e) {
                return ToolResult.failure(String.valueOf(e.getMessage()));
            }
        }
    }

    // -- mastery_note_gap -----------------------------------------------------

    /** 记录覆盖缺口（{@code mastery_note_gap}）。 */
    public static final class NoteGap implements Tool {

        @Override
        public String name() {
            return "mastery_note_gap";
        }

        @Override
        public String description() {
            return "Record a coverage gap found while learning (a goal requirement the "
                    + "materials do not cover, or a selected source nothing uses). Does not "
                    + "change the map.";
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
            String kind = ToolSupport.stripped(params.get("kind"));
            if (!"goal_gap".equals(kind) && !"source_omission".equals(kind)) {
                return ToolResult.failure("kind must be goal_gap or source_omission");
            }
            try {
                Map<String, Object> result = SharedTools.service(ctx).noteGap(
                        ctx.pathId(),
                        kind,
                        ToolSupport.str(params.get("ref")),
                        ToolSupport.str(params.get("reason")));
                return ToolResult.ok("Recorded a coverage gap.", result);
            } catch (PreconditionFailedException | IllegalArgumentException e) {
                return ToolResult.failure(String.valueOf(e.getMessage()));
            }
        }
    }
}
