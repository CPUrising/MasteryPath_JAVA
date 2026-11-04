package com.masterypath.domain.mode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 工具归属模式的静态分类表（spec 02 §一，ADR-005）。
 *
 * <p><b>fail-closed</b>：既不在 {@link #TOOL_MODES} 也不在 {@link #SHARED_TOOLS} 的工具
 * <b>不会</b>被静默当作共享工具——{@link #assertClassificationComplete()} 会拒绝它。
 *
 * <p>共享性用「<b>不在</b> {@link #TOOL_MODES} 里」表达，而不是把三个模式都列进去：
 * 这样「未声明工具」与「显式共享工具」才可区分，{@code TC-D9} 的不相交断言才有意义。
 */
public final class ToolClassification {

    /** outline 专属工具。 */
    public static final Set<String> OUTLINE_ONLY =
            Collections.unmodifiableSet(new LinkedHashSet<>(Set.of(
                    "mastery_build",
                    "mastery_revise")));

    /** study 与 review 共用工具。 */
    public static final Set<String> STUDY_REVIEW =
            Collections.unmodifiableSet(new LinkedHashSet<>(Set.of(
                    "mastery_quiz",
                    "mastery_grade",
                    "mastery_repair_question",
                    "mastery_assess",
                    "mastery_skip_question",
                    "mastery_defer_objective",
                    "mastery_note_explained",
                    "mastery_propose_objective",
                    "mastery_note_gap")));

    /** 显式共享工具：任意模式下均放行。 */
    public static final Set<String> SHARED_TOOLS =
            Collections.unmodifiableSet(new LinkedHashSet<>(Set.of(
                    "mastery_status",
                    "mastery_mode",
                    "mastery_profile",
                    "mastery_paths",
                    "mastery_switch",
                    "mastery_leave",
                    "mastery_topics",
                    "mastery_sessions",
                    "mastery_open_session",
                    "mastery_new_session",
                    "rag",
                    "read_source")));

    /** 模式专属工具 → 允许的模式集合。共享工具<b>不</b>出现在本表。 */
    public static final Map<String, Set<SessionMode>> TOOL_MODES;

    static {
        Map<String, Set<SessionMode>> table = new LinkedHashMap<>();
        Set<SessionMode> outline = Collections.unmodifiableSet(
                new LinkedHashSet<>(Set.of(SessionMode.OUTLINE)));
        Set<SessionMode> studyReview = Collections.unmodifiableSet(
                new LinkedHashSet<>(Set.of(SessionMode.STUDY, SessionMode.REVIEW)));
        for (String tool : OUTLINE_ONLY) {
            table.put(tool, outline);
        }
        for (String tool : STUDY_REVIEW) {
            table.put(tool, studyReview);
        }
        TOOL_MODES = Collections.unmodifiableMap(table);
    }

    private ToolClassification() {
    }

    /**
     * 全部 mastery 自有工具（共享 + 模式专属）。
     *
     * <p>只取 {@link #SHARED_TOOLS} 中以 {@code mastery_} 开头的项——{@code rag} 与
     * {@code read_source} 虽共享，但不属 mastery 命名空间，不计入本集合。
     */
    public static Set<String> allMasteryTools() {
        Set<String> names = new LinkedHashSet<>();
        for (String tool : SHARED_TOOLS) {
            if (tool.startsWith("mastery_")) {
                names.add(tool);
            }
        }
        names.addAll(TOOL_MODES.keySet());
        return Collections.unmodifiableSet(names);
    }

    /** 校验默认清单（{@link #allMasteryTools()}）是否完整归类。 */
    public static void assertClassificationComplete() {
        assertClassificationComplete(null);
    }

    /**
     * 完整性守卫（GUARD-1/2/3，{@code INV-17}）。
     *
     * <p>校验三件事：共享集合与专属集合不相交；清单内每个工具都被显式归类；
     * 不存在静默回退为共享的工具。
     *
     * @param toolNames 待校验的工具清单；{@code null} 时使用 {@link #allMasteryTools()}
     * @throws IllegalStateException 当存在重叠或未归类工具
     */
    public static void assertClassificationComplete(Set<String> toolNames) {
        Set<String> names = toolNames == null ? allMasteryTools() : new LinkedHashSet<>(toolNames);

        Set<String> overlap = new LinkedHashSet<>(SHARED_TOOLS);
        overlap.retainAll(TOOL_MODES.keySet());
        if (!overlap.isEmpty()) {
            throw new IllegalStateException(
                    "tools declared both shared and mode-exclusive: " + overlap);
        }

        Set<String> unclassified = new LinkedHashSet<>(names);
        unclassified.removeAll(TOOL_MODES.keySet());
        unclassified.removeAll(SHARED_TOOLS);
        if (!unclassified.isEmpty()) {
            throw new IllegalStateException(
                    "mastery tools not explicitly classified (fail-closed): " + unclassified);
        }
    }
}
