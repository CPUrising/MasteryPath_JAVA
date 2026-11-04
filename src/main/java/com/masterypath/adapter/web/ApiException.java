package com.masterypath.adapter.web;

/**
 * 带 HTTP 状态码的传输层异常（对应参考实现里逐端点抛出的 {@code HTTPException}）。
 *
 * <p>响应体统一为 {@code {"detail": "..."}}，与 FastAPI 的默认错误结构一致。
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String detail) {
        super(detail);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
