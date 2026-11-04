/**
 * 大纲构建与修订。
 *
 * <p>本包零 I/O、零网络、零 Spring：{@link com.masterypath.domain.outline.OutlineBuilder} 与
 * {@link com.masterypath.domain.outline.SourceResolver} 均可在无上下文条件下单测。
 *
 * <p>核心约束：
 * <ul>
 *   <li>{@code build} 是整图替换，非空路径必须 {@code confirm}（{@code INV-41}）；</li>
 *   <li>{@code revise} 是按 id 的差分操作，未触动知识点的进度逐字段保留；</li>
 *   <li>缺口必须显式生成并交用户选择，禁止静默重建或静默丢弃（{@code INV-23}）；</li>
 *   <li>payload 非法一律抛 {@code OutlineException} 且零状态变更（{@code INV-40}）。</li>
 * </ul>
 */
package com.masterypath.domain.outline;
