package com.masterypath.domain.mode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 模式门控判定（spec 02 §R2 / §R3）。
 *
 * <p><b>纯函数层</b>：零 I/O、零 Spring。门控在<b>调用期</b>生效，不在装配期——
 * 一个回合的工具 schema 在首轮之前一次性组装，因此全部 mastery 工具每回合都挂载。
 *
 * <p>两个归一函数的用法分工是本类的核心：
 * <ul>
 *   <li>{@link #toolIsAllowed} 用 {@link SessionMode#enforced}：未记录模式 → 不门控（{@code INV-09}）。</li>
 *   <li>{@link #admissionError} 用 {@link SessionMode#normalize}：未记录模式 → 当作 {@code study}。</li>
 * </ul>
 */
public final class ModeGate {

    private ModeGate() {
    }

    /**
     * 当前模式是否允许调用该工具。
     *
     * <p>未在 {@link ToolClassification#TOOL_MODES} 登记的工具视为共享，一律放行。
     *
     * @param toolName 工具名
     * @param mode     当前会话模式原始值，可为 {@code null}
     * @return 允许返回 {@code true}
     */
    public static boolean toolIsAllowed(String toolName, Object mode) {
        Set<SessionMode> allowed = ToolClassification.TOOL_MODES.get(toolName);
        if (allowed == null) {
            return true;
        }
        SessionMode current = SessionMode.enforced(mode);
        return current == null || allowed.contains(current);
    }

    /**
     * 该工具归属的模式，按 {@link SessionMode} 声明顺序返回。
     *
     * @return 共享工具或未登记工具返回空列表
     */
    public static List<SessionMode> owningModes(String toolName) {
        Set<SessionMode> allowed = ToolClassification.TOOL_MODES.get(toolName);
        if (allowed == null) {
            return List.of();
        }
        List<SessionMode> owners = new ArrayList<>();
        for (SessionMode candidate : SessionMode.values()) {
            if (allowed.contains(candidate)) {
                owners.add(candidate);
            }
        }
        return List.copyOf(owners);
    }

    /**
     * 拒绝文案：说明为何被拒，并给出唯一的纠正动作（spec 02 §R2）。
     *
     * @param toolName 被拒的工具
     * @param mode     当前模式原始值，可为 {@code null}
     * @return 可直接回给模型的文本
     */
    public static String wrongModeMessage(String toolName, Object mode) {
        List<SessionMode> targets = owningModes(toolName);
        SessionMode current = SessionMode.normalize(mode);
        if (targets.isEmpty()) {
            return toolName + " cannot run in '" + current.value() + "'.";
        }
        StringBuilder listed = new StringBuilder();
        for (int index = 0; index < targets.size(); index++) {
            if (index > 0) {
                listed.append(" or ");
            }
            listed.append('\'').append(targets.get(index).value()).append('\'');
        }
        return toolName + " belongs to the " + listed + " mode, and this conversation is in '"
                + current.value() + "'. Call mastery_mode with mode='"
                + targets.get(0).value() + "' first — tell the learner what you are switching to and why.";
    }

    /**
     * 该模式能否被进入；空串表示放行（spec 02 §R3）。
     *
     * <p>只有 {@code study} 有准入条件：路径必须已有大纲（任一模块含知识点）。
     *
     * @param mode       目标模式原始值，可为 {@code null}（按 {@code study} 判）
     * @param hasOutline 路径是否已有大纲
     * @return 拒绝文案，或空串
     */
    public static String admissionError(Object mode, boolean hasOutline) {
        if (SessionMode.normalize(mode) == SessionMode.STUDY && !hasOutline) {
            return "There is no outline to study yet. Stay in 'outline' and design one "
                    + "with the learner first.";
        }
        return "";
    }
}
