package com.masterypath.storage;

/**
 * CAS 重试耗尽（对应参考实现的 {@code ConcurrentModificationError} / {@code INV-11b}）。
 *
 * <p>仅在「乐观版本冲突重试 3 次仍失败」时抛出；前置条件失败 MUST NOT 抛本异常。
 */
public class CasConflictException extends RuntimeException {

    public CasConflictException(String message) {
        super(message);
    }

    public CasConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
