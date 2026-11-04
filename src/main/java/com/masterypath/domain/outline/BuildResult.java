package com.masterypath.domain.outline;

import com.masterypath.domain.model.LearningModule;
import com.masterypath.domain.model.SourceGap;

import java.util.List;

/**
 * {@code build} 的返回值（对应参考实现的 {@code BuildResult} dataclass）。
 *
 * @param status   {@code "built"} 或 {@code "refused_rebuild"}
 * @param modules  建出的模块（被拒绝时为空列表）
 * @param gaps     检测到的缺口（被拒绝时为空列表）
 * @param dropped  被拒绝时列出将丢失的知识点 id
 * @param rebuilt  是否替换了已有大纲
 * @param message  拒绝原因（成功时为空串）
 */
public record BuildResult(
        String status,
        List<LearningModule> modules,
        List<SourceGap> gaps,
        List<String> dropped,
        boolean rebuilt,
        String message) {

    public static final String BUILT = "built";
    public static final String REFUSED_REBUILD = "refused_rebuild";

    /** 成功建图。 */
    public static BuildResult built(List<LearningModule> modules,
                                    List<SourceGap> gaps,
                                    boolean rebuilt) {
        return new BuildResult(BUILT, List.copyOf(modules), List.copyOf(gaps),
                List.of(), rebuilt, "");
    }

    /** 拒绝重建：地图与进度保持不变。 */
    public static BuildResult refused(List<String> dropped, String message) {
        return new BuildResult(REFUSED_REBUILD, List.of(), List.of(),
                List.copyOf(dropped), false, message);
    }

    public boolean isBuilt() {
        return BUILT.equals(status);
    }
}
