package com.masterypath.adapter.tool;

import com.masterypath.domain.model.SourceGap;
import com.masterypath.domain.outline.BuildResult;
import com.masterypath.domain.outline.ReviseResult;
import com.masterypath.service.LearningService;
import com.masterypath.storage.PreconditionFailedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 大纲工具：{@code mastery_build} / {@code mastery_revise}。
 *
 * <p>两者都是 outline 专属（{@code ToolClassification.OUTLINE_ONLY}），
 * 执行前先做模式门控。
 */
public final class OutlineTools {

    private OutlineTools() {
    }

    // -- mastery_build --------------------------------------------------------

    /** 生成大纲；非空路径需 {@code confirm}（{@code mastery_build}）。 */
    public static final class Build implements Tool {

        @Override
        public String name() {
            return "mastery_build";
        }

        @Override
        public String description() {
            return "Generate the outline. Empty path creates; a path with objectives is a "
                    + "REBUILD and requires confirm=true.";
        }

        @Override
        public Map<String, Object> parameters() {
            return ToolSchemas.of(name());
        }

        @Override
        @SuppressWarnings("unchecked")
        public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            ToolResult refusal = ToolSupport.wrongMode(name(), ctx);
            if (refusal != null) {
                return refusal;
            }
            LearningService service = SharedTools.service(ctx);
            BuildResult result;
            try {
                Map<String, Object> payload = new java.util.LinkedHashMap<>();
                payload.put("modules", params.get("modules"));
                result = service.build(ctx.pathId(), payload, ToolSupport.bool(params.get("confirm")));
            } catch (PreconditionFailedException | IllegalArgumentException e) {
                return ToolResult.failure(String.valueOf(e.getMessage()));
            }
            List<Map<String, Object>> gaps = new ArrayList<>();
            for (SourceGap gap : result.gaps()) {
                gaps.add(StatePayload.map(
                        "id", gap.getId(),
                        "kind", gap.getKind() == null ? "" : gap.getKind().value(),
                        "ref", gap.getRef(),
                        "reason", gap.getReason()));
            }
            Map<String, Object> metadata = StatePayload.map(
                    "status", result.status(),
                    "module_count", result.modules().size(),
                    "gaps", gaps,
                    "dropped", result.dropped());
            String content = result.message().isEmpty()
                    ? "Built outline with " + result.modules().size() + " module(s)."
                    + (result.gaps().isEmpty() ? ""
                    : " " + result.gaps().size() + " source gap(s) need a decision.")
                    : result.message();
            if (result.isBuilt()) {
                Map<String, Object> state = StatePayload.of(
                        service, ctx.pathId(), ctx.sessionMode());
                metadata.put("map", state.get("map"));
                metadata.put("next", state.get("next"));
                content = content + "\nState: " + StatePayload.json(state);
            }
            return new ToolResult(result.isBuilt(), content, metadata);
        }
    }

    // -- mastery_revise -------------------------------------------------------

    /** 差分修订大纲（{@code mastery_revise}）。 */
    public static final class Revise implements Tool {

        @Override
        public String name() {
            return "mastery_revise";
        }

        @Override
        public String description() {
            return "Edit an existing outline with differential ops (progress-preserving).";
        }

        @Override
        public Map<String, Object> parameters() {
            return ToolSchemas.of(name());
        }

        @Override
        @SuppressWarnings("unchecked")
        public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            ToolResult refusal = ToolSupport.wrongMode(name(), ctx);
            if (refusal != null) {
                return refusal;
            }
            LearningService service = SharedTools.service(ctx);
            List<Map<String, Object>> ops = params.get("ops") instanceof List<?> list
                    ? (List<Map<String, Object>>) list : List.of();
            ReviseResult result;
            try {
                result = service.revise(ctx.pathId(), ops);
            } catch (PreconditionFailedException | IllegalArgumentException e) {
                return ToolResult.failure(String.valueOf(e.getMessage()));
            }
            Map<String, Object> state = StatePayload.of(
                    service, ctx.pathId(), ctx.sessionMode());
            List<Map<String, Object>> gaps = new ArrayList<>();
            for (SourceGap gap : result.gaps()) {
                gaps.add(StatePayload.map(
                        "id", gap.getId(),
                        "kind", gap.getKind() == null ? "" : gap.getKind().value(),
                        "ref", gap.getRef(),
                        "reason", gap.getReason()));
            }
            return ToolResult.ok(
                    "Revised outline; applied " + result.applied().size() + " op(s).\n"
                            + "State: " + StatePayload.json(state),
                    StatePayload.map(
                            "status", result.status(),
                            "applied", result.applied(),
                            "dropped_kp_ids", result.droppedKpIds(),
                            "gaps", gaps,
                            "map", state.get("map"),
                            "next", state.get("next")));
        }
    }
}
