package com.masterypath.adapter.llm;

/**
 * 供应商调用失败（参考实现 {@code LLMError}）。
 *
 * <p><b>消息必须安全可展示</b>：绝不包含上游响应体——上游可能回显密钥或内部信息。
 * 参考实现测试 {@code test_auth_error_is_structured_and_safe} 断言这一点。
 *
 * <p>{@link #retryable()} 只表示「重试可能成功」，不表示本类会自动重试；
 * 重试策略由调用方（回合循环或上层）决定。
 */
public class LlmError extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public LlmError(String message, String code, boolean retryable) {
        super(message);
        this.code = code == null ? "upstream_error" : code;
        this.retryable = retryable;
    }

    public LlmError(String message, String code) {
        this(message, code, false);
    }

    /** 结构化错误码：{@code auth_error} / {@code model_not_found} / {@code rate_limited} /
     *  {@code bad_request} / {@code upstream_error}。 */
    public String code() {
        return code;
    }

    /** 重试是否可能成功。 */
    public boolean retryable() {
        return retryable;
    }

    /**
     * 把 HTTP 状态码映射成结构化错误（参考实现 {@code _http_error}）。
     *
     * <p>六个分支的顺序与文案逐字复刻；<b>任何分支都不拼接上游响应体</b>。
     *
     * @param statusCode HTTP 状态码
     * @return 可直接抛出的错误
     */
    public static LlmError httpError(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return new LlmError(
                    "The provider rejected these credentials. Check the API key.",
                    "auth_error", false);
        }
        if (statusCode == 404) {
            return new LlmError(
                    "The configured model was not found at this provider.",
                    "model_not_found", false);
        }
        if (statusCode == 429) {
            return new LlmError(
                    "The provider is rate limiting requests. Try again later.",
                    "rate_limited", true);
        }
        if (statusCode == 400) {
            return new LlmError("The provider rejected the request.", "bad_request", false);
        }
        if (statusCode >= 500) {
            return new LlmError(
                    "The provider returned a server error.", "upstream_error", true);
        }
        return new LlmError(
                "The model provider returned HTTP " + statusCode + ".", "upstream_error", false);
    }
}
