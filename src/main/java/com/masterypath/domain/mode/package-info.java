/**
 * 模式门控纯函数层。
 *
 * <p>本包零 I/O、零网络、零 Spring：{@link com.masterypath.domain.mode.SessionMode}、
 * {@link com.masterypath.domain.mode.ToolClassification}、
 * {@link com.masterypath.domain.mode.ModeGate}、
 * {@link com.masterypath.domain.mode.BehaviorBudget} 均可在无上下文条件下单测。
 *
 * <p>门控在工具<b>调用期</b>生效（不在装配期）；分类表 fail-closed，
 * 未显式归类的工具由 {@code ToolClassification.assertClassificationComplete} 拒绝，
 * 而不是静默回退为共享。
 */
package com.masterypath.domain.mode;
