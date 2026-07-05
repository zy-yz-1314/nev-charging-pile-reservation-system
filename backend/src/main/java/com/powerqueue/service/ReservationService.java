package com.powerqueue.service;

import com.powerqueue.vo.ReservationVO;

import java.util.List;

/**
 * 预约 / 抢桩服务。
 */
public interface ReservationService {

    /**
     * 抢桩(高并发核心)。返回新订单 ID。
     * 三层防超卖:Redisson 分布式锁 + DB 乐观锁 + 唯一约束兜底。
     */
    Long grabPile(Long pileId);

    /**
     * 排队用户轮到确认占位(L1 队列)。指定 userId 抢占指定桩,
     * 复用与 grabPile 相同的分布式锁 + 乐观锁防超卖逻辑,返回新订单 ID。
     */
    Long createQueuedReservation(Long userId, Long pileId);

    /** 当前用户的预约/订单列表 */
    List<ReservationVO> myReservations();

    /** 开始充电(PENDING → CHARGING) */
    void startCharging(Long reservationId);

    /** 结束充电并结算(CHARGING → FINISHED) */
    void finish(Long reservationId);

    /** 取消预约(仅 PENDING 可取消) */
    void cancel(Long reservationId);
}
