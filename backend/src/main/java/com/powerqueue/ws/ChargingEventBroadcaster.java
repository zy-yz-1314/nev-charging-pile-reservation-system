package com.powerqueue.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

/**
 * 充电事件广播器(L3)。
 *
 * <p>监听 {@link ChargingEvent}:定向消息(targetUserId 非空)只发给该用户,
 * 普通消息按订阅(站点/桩)增量投递。定向消息在用户不在线时落入离线补偿队列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChargingEventBroadcaster {

    private final WsSessionRegistry registry;
    private final ObjectMapper objectMapper;

    @EventListener
    public void onChargingEvent(ChargingEvent event) {
        if (event.getTs() == 0L) {
            event.setTs(System.currentTimeMillis());
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.warn("事件序列化失败: {}", e.getMessage());
            return;
        }

        boolean directed = event.getTargetUserId() != null;
        boolean delivered = false;
        for (WsClient c : registry.all()) {
            boolean shouldSend = directed
                    ? event.getTargetUserId().equals(c.getUserId())
                    : c.subscribes(event.getStationId(), event.getPileId());
            if (!shouldSend) {
                continue;
            }
            if (send(c.getSession(), json)) {
                delivered = true;
            }
        }

        // 定向消息未投递 → 离线补偿
        if (directed && !delivered) {
            registry.storeOffline(event.getTargetUserId(), json);
        }
    }

    private boolean send(WebSocketSession session, String json) {
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("WebSocket 推送失败 sessionId={}: {}", session.getId(), e.getMessage());
        }
        return false;
    }

    /** 便捷构造:桩状态变更广播事件 */
    public static ChargingEvent pileState(Long stationId, Long pileId, String status, String message) {
        return ChargingEvent.builder()
                .type("PILE_STATE")
                .stationId(stationId)
                .pileId(pileId)
                .status(status)
                .message(message)
                .build();
    }

    /** 便捷构造:轮到指定用户(定向) */
    public static ChargingEvent queueTurn(Long userId, Long stationId, Long pileId, String message) {
        return ChargingEvent.builder()
                .type("QUEUE_TURN")
                .targetUserId(userId)
                .stationId(stationId)
                .pileId(pileId)
                .message(message)
                .build();
    }

    /** 便捷构造:漏充提醒(定向) */
    public static ChargingEvent planRemind(Long userId, String message) {
        return ChargingEvent.builder()
                .type("PLAN_REMIND")
                .targetUserId(userId)
                .message(message)
                .build();
    }

    /** 便捷构造:占位确认超时让位(定向) */
    public static ChargingEvent confirmTimeout(Long userId, Long pileId, String message) {
        return ChargingEvent.builder()
                .type("CONFIRM_TIMEOUT")
                .targetUserId(userId)
                .pileId(pileId)
                .message(message)
                .build();
    }

    @SuppressWarnings("unused")
    private static final List<String> TYPES = List.of("PILE_STATE", "QUEUE_TURN", "CONFIRM_TIMEOUT", "PRICE_CHANGE", "PLAN_REMIND");
}
