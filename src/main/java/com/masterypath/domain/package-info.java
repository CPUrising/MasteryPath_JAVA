/**
 * 领域层：实体、纯策略、判分、调度、模式门控。
 *
 * <p>硬性约束：本包及其子包 MUST NOT 依赖 Spring、JDBC 或任何网络类型；
 * 所有判定必须是纯函数（时间通过显式参数或 {@code java.time.Clock} 注入），
 * 以便在无上下文、无网络条件下单测。
 */
package com.masterypath.domain;
