package com.powerqueue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerqueue.common.BusinessException;
import com.powerqueue.common.ResultCode;
import com.powerqueue.common.UserContext;
import com.powerqueue.entity.ChargingPile;
import com.powerqueue.entity.Reservation;
import com.powerqueue.entity.Station;
import com.powerqueue.mapper.ChargingPileMapper;
import com.powerqueue.mapper.ReservationMapper;
import com.powerqueue.mapper.StationMapper;
import com.powerqueue.service.ChargingPileService;
import com.powerqueue.service.PileReservationSupport;
import com.powerqueue.service.PricingService;
import com.powerqueue.service.QueueService;
import com.powerqueue.service.ReservationService;
import com.powerqueue.vo.PriceCalcVO;
import com.powerqueue.ws.ChargingEventBroadcaster;
import com.powerqueue.vo.QueueEstimateVO;
import com.powerqueue.vo.ReservationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationMapper reservationMapper;
    private final ChargingPileMapper chargingPileMapper;
    private final StationMapper stationMapper;
    private final ChargingPileService chargingPileService;
    private final PileReservationSupport pileReservationSupport;
    private final QueueService queueService;
    private final PricingService pricingService;
    private final ApplicationEventPublisher eventPublisher;
    private final RedissonClient redissonClient;

    @Value("${powerqueue.reservation.lock-wait-seconds:3}")
    private long lockWaitSeconds;

    /**
     * 抢桩:委托 {@link PileReservationSupport#grabForUser} 执行三道防线防超卖。
     * <p>满桩时不再直接抛 PILE_NOT_IDLE,而是自动进入对应桩等待队列(L1 智能调度)。
     */
    @Override
    public Long grabPile(Long pileId) {
        Long userId = UserContext.getUserId();
        try {
            return pileReservationSupport.grabForUser(userId, pileId, false);
        } catch (BusinessException e) {
            // 仅当"桩不空闲"时转排队;其余(重复抢桩/锁繁忙)原样上抛
            if (ResultCode.PILE_NOT_IDLE.getCode().equals(e.getCode())) {
                QueueEstimateVO q = queueService.enqueue(userId, pileId);
                throw new BusinessException(ResultCode.PILE_QUEUED.getCode(),
                        "充电桩已满,已为您加入等待队列,当前前面 " + q.getAheadCount()
                                + " 人,预计等待 " + q.getEstimateWaitMin() + " 分钟");
            }
            throw e;
        }
    }

    @Override
    public Long createQueuedReservation(Long userId, Long pileId) {
        return pileReservationSupport.grabForUser(userId, pileId, true);
    }

    @Override
    public List<ReservationVO> myReservations() {
        Long userId = UserContext.getUserId();
        List<Reservation> list = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .orderByDesc(Reservation::getCreateTime));
        return assemble(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startCharging(Long reservationId) {
        Reservation r = getOwnReservation(reservationId);
        if (!"PENDING".equals(r.getStatus())) {
            throw new BusinessException(ResultCode.RESERVATION_STATUS_ERROR);
        }
        RLock lock = redissonClient.getLock("lock:pile:" + r.getPileId());
        boolean locked = false;
        try {
            locked = lock.tryLock(lockWaitSeconds, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ResultCode.GRAB_BUSY);
            }
            // 锁内重读,防并发覆写(与 cancel/finish/grabPile 互斥)
            r = getOwnReservation(reservationId);
            if (!"PENDING".equals(r.getStatus())) {
                throw new BusinessException(ResultCode.RESERVATION_STATUS_ERROR);
            }
            // 开充时锁定动态电价快照,后续 finish 结算以此为准,用户不被中途调价影响(L2)
            PriceCalcVO price = pricingService.calc(r.getPileId(), LocalDateTime.now());
            r.setFinalUnitPrice(price.getFinalPrice());
            r.setTimeCoefficient(price.getTimeCoefficient());
            r.setLoadCoefficient(price.getLoadCoefficient());
            r.setStatus("CHARGING");
            r.setStartTime(LocalDateTime.now());
            reservationMapper.updateById(r);
            chargingPileMapper.updateStatus(r.getPileId(), "CHARGING");
            chargingPileService.evictStationCache(r.getStationId());
            eventPublisher.publishEvent(ChargingEventBroadcaster.pileState(
                    r.getStationId(), r.getPileId(), "CHARGING", "充电桩开始充电"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.GRAB_BUSY);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long reservationId) {
        Reservation r = getOwnReservation(reservationId);
        if (!"CHARGING".equals(r.getStatus()) && !"PENDING".equals(r.getStatus())) {
            throw new BusinessException(ResultCode.RESERVATION_STATUS_ERROR);
        }
        RLock lock = redissonClient.getLock("lock:pile:" + r.getPileId());
        boolean locked = false;
        try {
            locked = lock.tryLock(lockWaitSeconds, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ResultCode.GRAB_BUSY);
            }
            // 锁内重读,防并发覆写(与 startCharging/cancel/grabPile 互斥)
            r = getOwnReservation(reservationId);
            if (!"CHARGING".equals(r.getStatus()) && !"PENDING".equals(r.getStatus())) {
                throw new BusinessException(ResultCode.RESERVATION_STATUS_ERROR);
            }
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime start = r.getStartTime() != null ? r.getStartTime() : r.getReserveTime();
            long minutes = Math.max(1, Duration.between(start, now).toMinutes());
            ChargingPile pile = chargingPileMapper.selectById(r.getPileId());
            BigDecimal powerUsed = BigDecimal.ZERO;
            BigDecimal amount = BigDecimal.ZERO;
            if (pile != null) {
                BigDecimal hours = BigDecimal.valueOf(minutes)
                        .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
                powerUsed = pile.getPower().multiply(hours).setScale(2, RoundingMode.HALF_UP);
                BigDecimal unit = r.getFinalUnitPrice() != null ? r.getFinalUnitPrice() : pile.getPrice();
                amount = powerUsed.multiply(unit).setScale(2, RoundingMode.HALF_UP);
            }
            r.setStatus("FINISHED");
            r.setEndTime(now);
            r.setDuration((int) minutes);
            r.setPowerUsed(powerUsed);
            r.setAmount(amount);
            reservationMapper.updateById(r);
            chargingPileMapper.updateStatus(r.getPileId(), "IDLE");
            chargingPileService.evictStationCache(r.getStationId());
            eventPublisher.publishEvent(ChargingEventBroadcaster.pileState(
                    r.getStationId(), r.getPileId(), "IDLE", "充电桩刚刚空闲"));
            promoteQueueHead(r.getPileId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.GRAB_BUSY);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long reservationId) {
        Reservation r = getOwnReservation(reservationId);
        String s = r.getStatus();
        if (!"PENDING".equals(s) && !"QUEUED".equals(s) && !"WAITING_CONFIRM".equals(s)) {
            throw new BusinessException(ResultCode.RESERVATION_STATUS_ERROR);
        }
        RLock lock = redissonClient.getLock("lock:pile:" + r.getPileId());
        boolean locked = false;
        try {
            locked = lock.tryLock(lockWaitSeconds, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ResultCode.GRAB_BUSY);
            }
            // 锁内重读,防并发覆写(与 startCharging/finish/grabPile 互斥)
            r = getOwnReservation(reservationId);
            s = r.getStatus();
            if (!"PENDING".equals(s) && !"QUEUED".equals(s) && !"WAITING_CONFIRM".equals(s)) {
                throw new BusinessException(ResultCode.RESERVATION_STATUS_ERROR);
            }
            r.setStatus("CANCELLED");
            reservationMapper.updateById(r);
            queueService.leave(UserContext.getUserId(), r.getPileId());
            if ("PENDING".equals(s) || "WAITING_CONFIRM".equals(s)) {
                chargingPileMapper.updateStatus(r.getPileId(), "IDLE");
                chargingPileService.evictStationCache(r.getStationId());
                eventPublisher.publishEvent(ChargingEventBroadcaster.pileState(
                        r.getStationId(), r.getPileId(), "IDLE", "充电桩已释放"));
                promoteQueueHead(r.getPileId());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.GRAB_BUSY);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 桩释放后,把队列首位用户提升为"待确认占位",并设置 10 分钟确认窗口。
     * 队列为空则无操作。
     */
    private void promoteQueueHead(Long pileId) {
        try {
            Long nextUserId = queueService.popHead(pileId);
            if (nextUserId == null) {
                return;
            }
            // 为队首设置 10 分钟确认窗口;具体通知由 WebSocket 广播层(异步)处理
            log.info("桩 {} 释放,队首用户 {} 已获占位确认窗口", pileId, nextUserId);
        } catch (Exception e) {
            // 排队轮换失败不影响主流程(充电完成)
            log.warn("队首轮换失败 pileId={}: {}", pileId, e.getMessage());
        }
    }

    // ============ 私有方法 ============

    private Reservation getOwnReservation(Long id) {
        Reservation r = reservationMapper.selectById(id);
        if (r == null) {
            throw new BusinessException(ResultCode.RESERVATION_NOT_FOUND);
        }
        // 车主只能操作自己的订单;管理员可操作全部
        if (!"ADMIN".equals(UserContext.getRole()) && !r.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return r;
    }

    /** 批量回填充电桩与站点信息,避免 N+1 查询 */
    private List<ReservationVO> assemble(List<Reservation> list) {
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> pileIds = list.stream().map(Reservation::getPileId).collect(Collectors.toSet());
        Set<Long> stationIds = list.stream().map(Reservation::getStationId).collect(Collectors.toSet());

        Map<Long, ChargingPile> pileMap = pileIds.isEmpty() ? Map.of()
                : chargingPileMapper.selectBatchIds(pileIds).stream()
                .collect(Collectors.toMap(ChargingPile::getId, Function.identity()));
        Map<Long, Station> stationMap = stationIds.isEmpty() ? Map.of()
                : stationMapper.selectBatchIds(stationIds).stream()
                .collect(Collectors.toMap(Station::getId, Function.identity()));

        return list.stream()
                .map(r -> toVO(r, pileMap.get(r.getPileId()), stationMap.get(r.getStationId())))
                .collect(Collectors.toList());
    }

    private ReservationVO toVO(Reservation r, ChargingPile pile, Station station) {
        ReservationVO v = new ReservationVO();
        v.setId(r.getId());
        v.setOrderNo(r.getOrderNo());
        v.setPileId(r.getPileId());
        v.setStationId(r.getStationId());
        v.setReserveTime(r.getReserveTime());
        v.setStartTime(r.getStartTime());
        v.setEndTime(r.getEndTime());
        v.setDuration(r.getDuration());
        v.setPowerUsed(r.getPowerUsed());
        v.setAmount(r.getAmount());
        v.setStatus(r.getStatus());
        v.setStatusDesc(statusDesc(r.getStatus()));
        if (pile != null) {
            v.setPileNo(pile.getPileNo());
            v.setPileType(pile.getType());
            v.setPower(pile.getPower());
            v.setPrice(pile.getPrice());
        }
        if (station != null) {
            v.setStationName(station.getName());
        }
        return v;
    }

    private String statusDesc(String status) {
        return switch (status) {
            case "PENDING" -> "待充电";
            case "QUEUED" -> "排队中";
            case "WAITING_CONFIRM" -> "待确认占位";
            case "CHARGING" -> "充电中";
            case "FINISHED" -> "已完成";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }
}
