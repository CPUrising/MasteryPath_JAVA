/**
 * 间隔复习调度（SM-2）。
 *
 * <p>本包零 I/O、零网络、零 Spring、<b>零 LLM</b>（{@code AGENTS.md} §4.1）：
 * {@link com.masterypath.domain.schedule.Sm2Scheduler} 只做纯算术，时间由调用方注入。
 *
 * <p>核心不变量：只有 SM-2 字段参与计算，{@code difficulty} / {@code stability} /
 * {@code retrievability} / {@code desiredRetention} 是 FSRS 字段，<b>仅展示</b>（{@code INV-31}）。
 */
package com.masterypath.domain.schedule;
