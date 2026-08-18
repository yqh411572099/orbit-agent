package com.butler.infrastructure.asr;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class AsrWebSocketConfig implements WebSocketConfigurer {

    private final AsrWebSocketHandler handler;

    public AsrWebSocketConfig(AsrWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 鉴权在 handler 内通过 query token 完成；允许同源，关闭 SockJS。
        registry.addHandler(handler, "/ws/asr").setAllowedOriginPatterns("*");
    }
}
