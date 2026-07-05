package com.powerqueue.config;

import com.powerqueue.ws.ChargingWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置(L3)。
 * 注册 /ws/charging 端点,放行跨域(前端直连)。端点不在 /api/** 下,
 * 不走 JWT 拦截器,鉴权在握手时由 {@link ChargingWebSocketHandler} 完成。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChargingWebSocketHandler chargingWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chargingWebSocketHandler, "/ws/charging")
                .setAllowedOrigins("http://localhost:5173");
    }
}
