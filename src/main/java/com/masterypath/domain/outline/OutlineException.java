package com.masterypath.domain.outline;

/**
 * build / revise 的 payload 非法（{@code INV-40}）。
 *
 * <p>继承 {@link IllegalArgumentException}，与参考实现的 {@code OutlineError(ValueError)} 对齐：
 * 调用方既可按具体类型捕获，也可按「非法参数」统一处理。
 *
 * <p>抛出本异常时 MUST 零状态变更——校验一律发生在改聚合之前，或由外层事务回滚兜底。
 */
public class OutlineException extends IllegalArgumentException {

    public OutlineException(String message) {
        super(message);
    }
}
