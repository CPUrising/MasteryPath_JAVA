/**
 * 工具契约层（{@code ADR-005}）。
 *
 * <p>只定义「工具长什么样」与「上下文携带什么」，不含任何具体工具——
 * 19 个 {@code Mastery*Tool} 与 schema 表在 {@code 0017} 落地。
 *
 * <p>门控在<b>调用期</b>由工具自己执行，不在装配期：一个回合的工具 schema 在首轮之前
 * 一次性组装，因此全部工具每回合都挂载；回合中途切模式，下一次调用立即生效。
 */
package com.masterypath.adapter.tool;
