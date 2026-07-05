package com.powerqueue.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 会话注册表 + 离线消息补偿(L3)。
 *
 * <p>单机维护 sessionId → {@link WsClient};多实例部署时,
 * 事件广播需额外通过 Redis Pub/Sub 跨实例投递(本实现预留扩展点)。
 *
 * <p>离线补偿:定向消息(如"轮到你了")在用户不在线时暂存 Redis List,
 * 用户下次连接时按顺序补推,保证关键消息不丢。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsSessionRegistry {

    private static final String OFFLINE_PREFIX = "ws:offline:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${powerqueue.ws.offline-ttl-days:7}")
    private long offlineTtlDays;

    private final ConcurrentHashMap<String, WsClient> clients = new ConcurrentHashMap<>();

    public void register(WebSocketSession session, Long userId) {
        clients.put(session.getId(), new WsClient(session, userId));
        flushOffline(userId, session);
    }

    public void remove(String sessionId) {
        clients.remove(sessionId);
    }

    public void subscribe(String sessionId, Set<Long> stationIds, Set<Long> pileIds) {
        WsClient c = clients.get(sessionId);
        if (c == null) {
            return;
        }
        if (stationIds != null) {
            c.getStationIds().addAll(stationIds);
        }
        if (pileIds != null) {
            c.getPileIds().addAll(pileIds);
        }
    }

    public Collection<WsClient> all() {
        return clients.values();
    }

    /** 暂存离线消息(定向消息专用) */
    public void storeOffline(Long userId, String json) {
        redisTemplate.opsForList().rightPush(OFFLINE_PREFIX + userId, json);
        redisTemplate.expire(OFFLINE_PREFIX + userId, offlineTtlDays, TimeUnit.DAYS);
    }

    /** 连接建立时补推离线消息 */
    private void flushOffline(Long userId, WebSocketSession session) {
        String key = OFFLINE_PREFIX + userId;
        Object msg;
        int n = 0;
        while ((msg = redisTemplate.opsForList().leftPop(key)) != null) {
            sendQuietly(session, msg.toString());
            n++;
        }
        if (n > 0) {
            log.info("用户 {} 补推 {} 条离线消息", userId, n);
        }
    }

    private void sendQuietly(WebSocketSession session, String json) {
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (Exception e) {
            log.warn("离线消息补推失败: {}", e.getMessage());
        }
    }
}
