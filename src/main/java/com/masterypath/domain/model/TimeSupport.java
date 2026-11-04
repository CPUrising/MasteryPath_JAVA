package com.masterypath.domain.model;

/**
 * 时间工具：参考实现用 {@code time.time()}（epoch 秒，浮点），此处保持一致，
 * 以便与旧库中的时间戳直接比较而无需换算。
 *
 * <p>业务判定不直接调用本类：需要「当前时间」的纯函数 MUST 通过参数注入，
 * 只有实体默认值才使用它。
 */
public final class TimeSupport {

    private TimeSupport() {
    }

    /** 当前 epoch 秒（含小数），等价于 Python 的 {@code time.time()}。 */
    public static double nowSeconds() {
        return System.currentTimeMillis() / 1000.0;
    }
}
