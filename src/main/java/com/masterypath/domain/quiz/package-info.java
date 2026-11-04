/**
 * 出题参数校验与规范化。
 *
 * <p>本包零 I/O、零 Spring：{@link com.masterypath.domain.quiz.QuizSpec} 只做参数校验与
 * 题型推断，把模型给的松散参数变成服务端可长期持有的 {@code PendingQuestion}。
 *
 * <p>核心约束（{@code INV-35}）：开放题的关键词 MUST 在<b>注册时</b>固定，
 * 判分时刻不再访问模型，因此判分是确定性的。
 */
package com.masterypath.domain.quiz;
