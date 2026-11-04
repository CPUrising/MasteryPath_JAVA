package com.masterypath.adapter.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS：允许任意来源（参考实现 {@code _cors_origins()} 默认返回 {@code ["*"]}）。
 *
 * <p><b>用 {@code allowedOrigins("*")} 而不是 {@code allowedOriginPatterns("*")}</b>：
 * 后者会把请求的 {@code Origin} 原样回显（{@code access-control-allow-origin: http://localhost:3000}），
 * 而参考实现与前端契约要求字面量 {@code *}（{@code test_f0_api.py#test_cors_allows_frontend_origin}）。
 * Spring 在 {@code allowCredentials=false} 时对 {@code allowedOrigins("*")} 返回字面量 {@code *}。
 *
 * <p>不使用凭据（{@code allowCredentials=false}），因此通配来源不构成安全风险。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
