/**
 * 确定性判分。
 *
 * <p>本包零 I/O、零网络、零 Spring、<b>零 LLM</b>（{@code INV-32}）：
 * 全部参照物在题目注册时已固定，{@link com.masterypath.domain.grading.Grading} 只做字符串比较。
 *
 * <p>核心不变量：无法解析的作答返回 {@code ungraded} 而非 {@code incorrect}，
 * 使学习者的猜测不被记成负面（{@code INV-46}）。
 */
package com.masterypath.domain.grading;
