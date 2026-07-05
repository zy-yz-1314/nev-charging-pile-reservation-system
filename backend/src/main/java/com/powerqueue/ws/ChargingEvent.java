package com.powerqueue.ws;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 充电桩状态变更事件(L3)。
 * <p>事件驱动链路:状态变更 → {@code publishEvent} → {@link ChargingEventBroadcaster} 统一广播 → 前端增量局部刷新。
 *
 * <p>事件类型:
 * <ul>
 *   <li>PILE_STATE —— 桩状态变更(空闲/占用/充电中),按订阅的 station/pile 增量推送;</li>
 *   <li>QUEUE_TURN —— 轮到该用户(定向 targetUserId),带 10 分钟确认窗口提示;</li>
 *   <li>CONFIRM_TIMEOUT —— 确认超时让位;PRICE_CHANGE —— 档位变化;PLAN_REMIND —— 漏充提醒。</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingEvent {

    /** PILE_STATE / QUEUE_TURN / CONFIRM_TIMEOUT / PRICE_CHANGE / PLAN_REMIND */
    private String type;
    private Long stationId;
    private Long pileId;
    private String status;
    private BigDecimal idleRate;
    private String loadLevel;
    private BigDecimal finalPrice;
    /** 定向消息目标用户;为空则广播给订阅者 */
    private Long targetUserId;
    private String message;
    private long ts;
}
