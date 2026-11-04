package com.masterypath.adapter.web;

import com.masterypath.domain.outline.OutlineException;
import com.masterypath.domain.quiz.QuizSpec;
import com.masterypath.storage.PreconditionFailedException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 兜底异常映射（{@code 0018-rest-api/design.md} 决策 15）。
 *
 * <p><b>不是一刀切</b>：需要非默认状态码的端点（{@code override} 的 412、
 * {@code rename} / {@code redo} 的 404）在控制器里显式 {@code try/catch} 并抛
 * {@link ApiException}，本类只处理「默认语义」与漏网的异常。
 *
 * <p>响应体统一 {@code {"detail": "..."}}，与 FastAPI 一致。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** 显式状态码优先。 */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException e) {
        return body(e.status(), e.getMessage());
    }

    /** 前置条件失败默认 409（{@code override} 端点自行改成 412）。 */
    @ExceptionHandler(PreconditionFailedException.class)
    public ResponseEntity<Map<String, Object>> handlePrecondition(PreconditionFailedException e) {
        return body(409, e.getMessage());
    }

    /** 大纲 payload 非法 → 422（参考实现 {@code OutlineError}）。 */
    @ExceptionHandler(OutlineException.class)
    public ResponseEntity<Map<String, Object>> handleOutline(OutlineException e) {
        return body(422, e.getMessage());
    }

    /** 出题参数非法 → 422（参考实现同样抛 {@code OutlineError}）。 */
    @ExceptionHandler(QuizSpec.QuizException.class)
    public ResponseEntity<Map<String, Object>> handleQuiz(QuizSpec.QuizException e) {
        return body(422, e.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> body(int status, String detail) {
        return ResponseEntity.status(HttpStatus.valueOf(status))
                .body(Map.of("detail", detail == null ? "" : detail));
    }
}
