/**
 * 学习者可见投影。
 *
 * <p>本包零 I/O、零 Spring：投影是纯函数，输入服务端完整对象，输出可安全下发的白名单视图。
 *
 * <p>硬性约束（{@code INV-07}）：学习者可见投影 MUST NOT 含 {@code expected_answer} /
 * {@code explanation}，也不得含开放题关键词与内部时间戳。实现一律用白名单构造，
 * 禁止用「排除敏感字段」的黑名单写法。
 */
package com.masterypath.domain.projection;
