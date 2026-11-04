package com.masterypath.adapter.tool;

import com.masterypath.domain.mode.ToolClassification;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具注册表与条件挂载（参考实现 {@code TOOLS} / {@code mounted_tools}）。
 *
 * <p><b>全部工具每回合都挂载</b>，门控在调用期由工具自己执行（{@code ADR-005}）——
 * 因此回合中途切模式，下一次工具调用立即生效，无需重新挂载 schema。
 *
 * <p>唯一的例外是<b>条件挂载</b>（{@code ADR-023}）：{@code rag} 只在有知识库时挂载，
 * {@code read_source} 只在有来源时挂载。没有输入的工具不该出现在 schema 里，
 * 否则模型会调用一个必然失败的接口。
 */
public final class MasteryTools {

    /** 全部工具（不可变）。 */
    private static final Map<String, Tool> ALL = build();

    private MasteryTools() {
    }

    /** 全部工具（只读）。 */
    public static Map<String, Tool> all() {
        return ALL;
    }

    /**
     * 按上下文条件挂载工具（参考实现 {@code mounted_tools}）。
     *
     * <p>返回<b>新 map</b>，不改 {@link #all()}——否则一次条件挂载会污染后续回合。
     *
     * @param ctx 回合上下文
     * @return 可挂载的工具表
     */
    public static Map<String, Tool> mountedTools(ToolContext ctx) {
        Map<String, Tool> mounted = new LinkedHashMap<>(ALL);
        if (ctx.kbIds().isEmpty()) {
            mounted.remove("rag");
        }
        if (ctx.sourceIndex().isEmpty()) {
            mounted.remove("read_source");
        }
        return mounted;
    }

    // -- 构造 -----------------------------------------------------------------

    private static Map<String, Tool> build() {
        Map<String, Tool> tools = new LinkedHashMap<>();
        register(tools, new SharedTools.Status());
        register(tools, new SharedTools.Mode());
        register(tools, new SharedTools.Paths());
        register(tools, new SharedTools.Switch());
        register(tools, new SharedTools.Leave());
        register(tools, new OutlineTools.Build());
        register(tools, new OutlineTools.Revise());
        register(tools, new StudyTools.Quiz());
        register(tools, new StudyTools.Grade());
        register(tools, new StudyTools.SkipQuestion());
        register(tools, new StudyTools.NoteExplained());
        register(tools, new StudyTools.DeferObjective());
        register(tools, new RepairTools.RepairQuestion());
        register(tools, new RepairTools.Profile());
        register(tools, new RepairTools.Assess());
        register(tools, new RepairTools.ProposeObjective());
        register(tools, new RepairTools.NoteGap());
        register(tools, new SourceTools.Rag());
        register(tools, new SourceTools.ReadSource());
        register(tools, new NavTools.Topics());
        register(tools, new NavTools.Sessions());
        register(tools, new NavTools.OpenSession());
        register(tools, new NavTools.NewSession());
        // fail-closed：每个挂载的工具都必须被显式归类（INV-17）。
        ToolClassification.assertClassificationComplete(tools.keySet());
        return Map.copyOf(tools);
    }

    private static void register(Map<String, Tool> tools, Tool tool) {
        tools.put(tool.name(), tool);
    }
}
