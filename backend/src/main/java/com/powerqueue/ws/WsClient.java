package com.powerqueue.ws;

import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一个 WebSocket 连接对应的客户端上下文:持有会话、所属用户、订阅的站点/桩位。
 * 订阅集合为并发安全,支持运行时增量订阅。
 */
public class WsClient {

    private final WebSocketSession session;
    private final Long userId;
    /** 订阅的充电站(状态变更时按站点过滤) */
    private final Set<Long> stationIds = ConcurrentHashMap.newKeySet();
    /** 订阅的具体充电桩 */
    private final Set<Long> pileIds = ConcurrentHashMap.newKeySet();

    public WsClient(WebSocketSession session, Long userId) {
        this.session = session;
        this.userId = userId;
    }

    public WebSocketSession getSession() {
        return session;
    }

    public Long getUserId() {
        return userId;
    }

    public Set<Long> getStationIds() {
        return stationIds;
    }

    public Set<Long> getPileIds() {
        return pileIds;
    }

    /** 是否订阅了指定站点或桩 */
    public boolean subscribes(Long stationId, Long pileId) {
        return (stationId != null && stationIds.contains(stationId))
                || (pileId != null && pileIds.contains(pileId));
    }
}
