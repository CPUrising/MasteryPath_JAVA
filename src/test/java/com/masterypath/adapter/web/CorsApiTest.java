package com.masterypath.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CORS 契约（{@code test_f0_api.py#test_cors_allows_frontend_origin}）。
 *
 * <p>必须走完整 Spring 上下文：{@link CorsConfig} 是 {@code WebMvcConfigurer}，
 * {@code standaloneSetup} 不会应用它。
 *
 * <p>断言字面量 {@code *} 而非回显来源——前端与参考实现都依赖这个值。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void allowsAnyOriginWithLiteralStar() throws Exception {
        String header = mvc.perform(get("/learning/paths")
                        .header("Origin", "http://localhost:3000"))
                .andReturn().getResponse().getHeader("access-control-allow-origin");
        assertThat(header).isEqualTo("*");
    }
}
