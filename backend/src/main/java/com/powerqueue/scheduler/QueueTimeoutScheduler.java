package com.powerqueue.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerqueue.entity.Reservation;
import com.powerqueue.mapper.ReservationMapper;
import com.powerqueue.service.QueueService;
import com.powerqueue.ws.ChargingEventBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 占位确认超时让位定时任务(L1)。
 * <p>
 * 队首用户轮到后享有 {@code confirm-window-seconds} 的确认窗口。若超时未确认,
 * 本任务每分钟扫描 {@code status=WAITING_CONFIRM} 且 {@code reserve_time} 已过期的订单:
 * <ol>
 *   <li>获取与抢桩相同的 Redisson 桩锁 {@code lock:pile:{pileId}},与 grabForUser 串行化;</li>
 *   <li>锁内重查订单状态——快照与处理之间的间隙里用户可能已 confirm,此时跳过;</li>
 *   <li>确认仍为 WAITING_CONFIRM 后:移出队列并取消订单(leave)、提升下一位(popHead);</li>
 *   <li>leave 成功后再发 CONFIRM_TIMEOUT 定向通知(离线时落入补偿队列)。</li>
 * </ol>
 * 闭环"满桩入队 → 轮到确认 → 超时让位"的最后一步,避免空闲桩被不响应的人虚占。
 * <p>
 * 并发安全:扫描依赖内存快照,快照返回后用户仍可能完成 confirm。若不加锁直接 leave+popHead,
 * 会把已确认占用的桩误判为超时,弹出下一位却让其确认必失败(队列静默丢人)。
 * 故取同一把桩锁 + 锁内重检状态,确保"让位"与"用户确认"互斥。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueTimeoutScheduler {

    private final ReservationMapper reservationMapper;
    private final QueueService queueService;
    private final ApplicationEventPublisher eventPublisher;
    private final RedissonClient redissonClient;

    @Value("${powerqueue.queue.confirm-window-seconds:600}")
    private long confirmWindowSeconds;
    @Value("${powerqueue.reservation.lock-wait-seconds:3}")
    private long lockWaitSeconds;
    @Value("${powerqueue.reservation.lock-lease-seconds:5}")
    private long lockLeaseSeconds;

    @Scheduled(cron = "${powerqueue.queue.timeout-cron}")
    public void sweepTimeoutConfirm() {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(confirmWindowSeconds);
        List<Reservation> timeouts = reservationMapper.selectList(
                new LambdaQueryWrapper<Reservation>()
                        .eq(Reservation::getStatus, "WAITING_CONFIRM")
                        .lt(Reservation::getReserveTime, deadline));
        if (timeouts.isEmpty()) {
            return;
        }
        log.info("扫描到 {} 笔超时未确认占位订单,开始让位轮换", timeouts.size());
        for (Reservation r : timeouts) {
            handleOne(r);
        }
    }

    /**
     * 单笔超时订单的让位处理。加桩锁 + 锁内重检,与 {@code grabForUser} 互斥,
     * 消除"快照固化后用户 confirm 导致下一位被静默丢弃"的竞态。
     */
    private void handleOne(Reservation r) {
        RLock lock = redissonClient.getLock("lock:pile:" + r.getPileId());
        boolean locked = false;
        try {
            locked = lock.tryLock(lockWaitSeconds, lockLeaseSeconds, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("超时让位未获取桩锁 pileId={},跳过本轮", r.getPileId());
                return;
            }
            // 锁内重检:快照后用户可能已 confirm,此时订单已非 WAITING_CONFIRM,必须跳过
            Reservation fresh = reservationMapper.selectById(r.getId());
            if (fresh == null || !"WAITING_CONFIRM".equals(fresh.getStatus())) {
                log.info("订单 {} 已被确认或处理({}),跳过让位",
                        r.getId(), fresh == null ? "null" : fresh.getStatus());
                return;
            }
            queueService.leave(r.getUserId(), r.getPileId());
            queueService.popHead(r.getPileId());
            // leave 成功后再通知,避免"用户已确认却收到超时提示"的错乱
            eventPublisher.publishEvent(ChargingEventBroadcaster.confirmTimeout(
                    r.getUserId(), r.getPileId(), "您的占位确认已超时,已为下一位让位"));
            log.info("用户 {} 在桩 {} 的占位确认超时,已让位并轮换队首",
                    r.getUserId(), r.getPileId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("超时让位等待桩锁被中断 pileId={}", r.getPileId());
        } catch (Exception e) {
            log.warn("超时让位处理失败 userId={} pileId={}: {}",
                    r.getUserId(), r.getPileId(), e.getMessage());
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
