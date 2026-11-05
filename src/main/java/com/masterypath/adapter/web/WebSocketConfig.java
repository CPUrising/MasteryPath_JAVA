package com.masterypath.adapter.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 注册 {@code /ws} 端点（参考实现 {@code @app.websocket("/ws")}）。
 *
 * <p>允许任意来源，与 {@link CorsConfig} 的 {@code *} 一致：前端与后端不同源部署。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WsTurnHandler handler;

    public WebSocketConfig(WsTurnHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws").setAllowedOriginPatterns("*");
    }
}
