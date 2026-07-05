package com.powerqueue.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerqueue.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 充电实时推送 WebSocket 端点 /ws/charging(L3)。
 *
 * <p>握手鉴权:连接 URL 携带 ?token={jwt},服务端解析校验后绑定 userId;
 * 客户端连接后发送 {type:"SUBSCRIBE", stationIds:[], pileIds:[]} 订阅增量;
 * 心跳:客户端发 {type:"PING"},服务端回 PONG(替换前端 5 秒轮询)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChargingWebSocketHandler extends TextWebSocketHandler {

    private final WsSessionRegistry registry;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = authenticate(session);
        if (userId == null) {
            close(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        // 将 userId 绑定到会话属性,便于后续读取
        session.getAttributes().put("userId", userId);
        registry.register(session, userId);
        log.info("WebSocket 已连接: userId={}, sessionId={}", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String type = node.path("type").asText();
            if ("PING".equals(type)) {
                sendMessage(session, "{\"type\":\"PONG\"}");
                return;
            }
            if ("SUBSCRIBE".equals(type)) {
                Set<Long> stationIds = readLongSet(node.get("stationIds"));
                Set<Long> pileIds = readLongSet(node.get("pileIds"));
                registry.subscribe(session.getId(), stationIds, pileIds);
            }
        } catch (Exception e) {
            log.warn("WebSocket 消息处理失败: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.remove(session.getId());
        log.info("WebSocket 已断开: sessionId={}, status={}", session.getId(), status);
    }

    private Long authenticate(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        String token = extractQuery(uri.getQuery(), "token");
        if (token == null || token.isBlank()) {
            return null;
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        try {
            Claims claims = jwtUtil.parse(token);
            return Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            log.warn("WebSocket 鉴权失败: {}", e.getMessage());
            return null;
        }
    }

    private String extractQuery(String query, String key) {
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0 && key.equals(pair.substring(0, idx))) {
                return pair.substring(idx + 1);
            }
        }
        return null;
    }

    private Set<Long> readLongSet(JsonNode node) {
        Set<Long> set = new HashSet<>();
        if (node != null && node.isArray()) {
            for (JsonNode n : node) {
                if (n.isNumber()) {
                    set.add(n.asLong());
                }
            }
        }
        return set;
    }

    private void sendMessage(WebSocketSession session, String json) {
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (Exception e) {
            log.warn("WebSocket 发送失败: {}", e.getMessage());
        }
    }

    private void close(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unused")
    private static final Map<String, String> TYPE_DESC = Map.of(
            "SUBSCRIBE", "订阅", "PING", "心跳", "PILE_STATE", "桩状态");
}
