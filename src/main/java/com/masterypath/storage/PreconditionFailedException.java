package com.masterypath.storage;

/**
 * 前置条件失败（对应参考实现的 {@code PreconditionError} / {@code INV-11c}）。
 *
 * <p>语义要求：本异常 MUST NOT 触发重试，且失败的事务 MUST NOT 留下任何状态或事件。
 */
public class PreconditionFailedException extends RuntimeException {

    public PreconditionFailedException(String message) {
        super(message);
    }

    public PreconditionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
