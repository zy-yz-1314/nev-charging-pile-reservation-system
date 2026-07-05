package com.powerqueue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerqueue.common.BusinessException;
import com.powerqueue.common.ResultCode;
import com.powerqueue.entity.ChargingPile;
import com.powerqueue.entity.Reservation;
import com.powerqueue.mapper.ChargingPileMapper;
import com.powerqueue.mapper.ReservationMapper;
import com.powerqueue.ws.ChargingEventBroadcaster;import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 抢桩核心逻辑(防超卖三道防线),从 ReservationService 抽出,供:
 * <ul>
 *   <li>{@link ReservationService#grabPile} —— 车主直接抢桩;</li>
 *   <li>{@link QueueService#confirm} —— 排队用户轮到确认占位。</li>
 * </ul>
 * 两处复用同一份分布式锁 + 乐观锁防超卖逻辑,避免代码重复,也避免 Service 间构造注入循环依赖。
 *
 * <p>三道防线:
 * <ol>
 *   <li>Redisson 分布式锁,同一充电桩并发请求串行化;</li>
 *   <li>锁内重读 + DB 乐观锁更新(status='IDLE' AND version=?),按影响行数判定;</li>
 *   <li>reservation(pile_id,status) 索引兜底。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PileReservationSupport {

    private static final DateTimeFormatter ORDER_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ChargingPileMapper chargingPileMapper;
    private final ReservationMapper reservationMapper;
    private final ChargingPileService chargingPileService;
    private final RedissonClient redissonClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${powerqueue.reservation.lock-wait-seconds:3}")
    private long lockWaitSeconds;
    @Value("${powerqueue.reservation.lock-lease-seconds:5}")
    private long lockLeaseSeconds;

    /**
     * 为指定用户抢占指定桩。抢到返回新订单 ID,失败抛业务异常。
     *
     * @param userId  抢桩用户(直接抢桩取 UserContext,排队确认由队列传入)
     * @param pileId  目标充电桩
     * @param queued  是否来自排队确认(影响日志与队列态标记)
     */
    @Transactional(rollbackFor = Exception.class)
    public Long grabForUser(Long userId, Long pileId, boolean queued) {
        chargingPileService.getByIdOrThrow(pileId);

        // 用户维度锁防同用户跨桩并发抢桩(CRITICAL-B:active 检查移入锁内,防 TOCTOU)
        RLock userLock = redissonClient.getLock("lock:user:" + userId);
        boolean userLocked = false;
        try {
            userLocked = userLock.tryLock(lockWaitSeconds, TimeUnit.SECONDS);
            if (!userLocked) {
                throw new BusinessException(ResultCode.GRAB_BUSY);
            }
            // 防重复抢桩:同一用户不能有进行中的订单(锁内检查)
            Long active = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                    .eq(Reservation::getUserId, userId)
                    .in(Reservation::getStatus, "PENDING", "QUEUED", "WAITING_CONFIRM", "CHARGING"));
            if (active != null && active > 0) {
                throw new BusinessException(ResultCode.DUPLICATE_RESERVATION);
            }

            RLock lock = redissonClient.getLock("lock:pile:" + pileId);
            boolean locked = false;
            try {
                locked = lock.tryLock(lockWaitSeconds, TimeUnit.SECONDS);
                if (!locked) {
                    throw new BusinessException(ResultCode.GRAB_BUSY);
                }
                ChargingPile fresh = chargingPileService.getByIdOrThrow(pileId);
                // 直接抢桩只接 IDLE;排队确认可接 IDLE 或 RESERVED(popHead 已预留)
                boolean available = "IDLE".equals(fresh.getStatus())
                        || (queued && "RESERVED".equals(fresh.getStatus()));
                if (!available) {
                    throw new BusinessException(ResultCode.PILE_NOT_IDLE);
                }
                int affected = chargingPileMapper.grabPile(pileId, fresh.getVersion());
                if (affected == 0) {
                    throw new BusinessException(ResultCode.PILE_NOT_IDLE);
                }

                Reservation r = new Reservation();
                r.setOrderNo(generateOrderNo());
                r.setUserId(userId);
                r.setPileId(pileId);
                r.setStationId(fresh.getStationId());
                r.setReserveTime(LocalDateTime.now());
                r.setStatus("PENDING");
                r.setAmount(BigDecimal.ZERO);
                reservationMapper.insert(r);

                chargingPileService.evictStationCache(fresh.getStationId());
                // 广播桩状态变更 → 订阅该站/该桩的连接增量刷新(L3)
                eventPublisher.publishEvent(ChargingEventBroadcaster.pileState(
                        fresh.getStationId(), pileId, "RESERVED", "充电桩已被预约"));
                log.info("用户 {} 抢桩成功(queued={}), pileId={}, orderNo={}", userId, queued, pileId, r.getOrderNo());
                return r.getId();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ResultCode.GRAB_BUSY);
            } finally {
                if (locked && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.GRAB_BUSY);
        } finally {
            if (userLocked && userLock.isHeldByCurrentThread()) {
                userLock.unlock();
            }
        }
    }

    /** 基于 Redis 自增序列生成全局唯一订单号 */
    private String generateOrderNo() {
        Long seq = redisTemplate.opsForValue().increment("order:seq");
        if (seq == null) {
            seq = System.currentTimeMillis();
        }
        return "PQ" + LocalDateTime.now().format(ORDER_TIME_FMT) + String.format("%05d", seq % 100000);
    }
}
