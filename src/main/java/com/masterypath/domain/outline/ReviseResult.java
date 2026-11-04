package com.masterypath.domain.outline;

import com.masterypath.domain.model.SourceGap;

import java.util.List;

/**
 * {@code revise} 的返回值（对应参考实现的 {@code ReviseResult} dataclass）。
 *
 * @param status      固定 {@code "revised"}
 * @param applied     已应用的 op 摘要，形如 {@code add_kp:k3}
 * @param droppedKpIds 被删除或随模块一并删除的知识点 id
 * @param gaps        重算后的缺口
 * @param message     附注（当前恒为空串）
 */
public record ReviseResult(
        String status,
        List<String> applied,
        List<String> droppedKpIds,
        List<SourceGap> gaps,
        String message) {

    public static final String REVISED = "revised";
}
