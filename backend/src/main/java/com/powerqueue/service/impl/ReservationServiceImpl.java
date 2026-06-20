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
import com.powerqueue.service.ReservationService;
import com.powerqueue.vo.ReservationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private static final DateTimeFormatter ORDER_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ReservationMapper reservationMapper;
    private final ChargingPileMapper chargingPileMapper;
    private final StationMapper stationMapper;
    private final ChargingPileService chargingPileService;
    private final RedissonClient redissonClient;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${powerqueue.reservation.lock-wait-seconds:3}")
    private long lockWaitSeconds;
    @Value("${powerqueue.reservation.lock-lease-seconds:5}")
    private long lockLeaseSeconds;

    /**
     * 抢桩核心流程(防超卖三道防线):
     * <ol>
     *   <li>Redisson 分布式锁,把同一充电桩的并发请求串行化;</li>
     *   <li>锁内重读状态 + DB 乐观锁更新(status='IDLE' AND version=?),按影响行数判定;</li>
     *   <li>reservation(pile_id,status) 索引/唯一约束兜底。</li>
     * </ol>
     * 即使锁在事务提交前释放,后续请求也会因 version 已自增而更新失败,不会超卖。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long grabPile(Long pileId) {
        Long userId = UserContext.getUserId();

        // 桩必须存在
        chargingPileService.getByIdOrThrow(pileId);

        // 防重复抢桩:同一用户不能有进行中的订单
        Long active = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .in(Reservation::getStatus, "PENDING", "CHARGING"));
        if (active != null && active > 0) {
            throw new BusinessException(ResultCode.DUPLICATE_RESERVATION);
        }

        RLock lock = redissonClient.getLock("lock:pile:" + pileId);
        boolean locked = false;
        try {
            locked = lock.tryLock(lockWaitSeconds, lockLeaseSeconds, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ResultCode.GRAB_BUSY);
            }
            // 锁内重读最新状态
            ChargingPile fresh = chargingPileService.getByIdOrThrow(pileId);
            if (!"IDLE".equals(fresh.getStatus())) {
                throw new BusinessException(ResultCode.PILE_NOT_IDLE);
            }
            // DB 乐观锁更新:仅当仍为 IDLE 且版本匹配时成功
            int affected = chargingPileMapper.grabPile(pileId, fresh.getVersion());
            if (affected == 0) {
                throw new BusinessException(ResultCode.PILE_NOT_IDLE);
            }
            // 创建预约订单
            Reservation r = new Reservation();
            r.setOrderNo(generateOrderNo());
            r.setUserId(userId);
            r.setPileId(pileId);
            r.setStationId(fresh.getStationId());
            r.setReserveTime(LocalDateTime.now());
            r.setStatus("PENDING");
            r.setAmount(BigDecimal.ZERO);
            reservationMapper.insert(r);

            // 失效该站点充电桩缓存,保证实时状态
            chargingPileService.evictStationCache(fresh.getStationId());
            log.info("用户 {} 抢桩成功, pileId={}, orderNo={}", userId, pileId, r.getOrderNo());
            return r.getId();
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
    public List<ReservationVO> myReservations() {
        Long userId = UserContext.getUserId();
        List<Reservation> list = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .orderByDesc(Reservation::getCreateTime));
        return assemble(list);
    }

    @Override
    public void startCharging(Long reservationId) {
        Reservation r = getOwnReservation(reservationId);
        if (!"PENDING".equals(r.getStatus())) {
            throw new BusinessException(ResultCode.RESERVATION_STATUS_ERROR);
        }
        r.setStatus("CHARGING");
        r.setStartTime(LocalDateTime.now());
        reservationMapper.updateById(r);
        chargingPileMapper.updateStatus(r.getPileId(), "CHARGING");
        chargingPileService.evictStationCache(r.getStationId());
    }

    @Override
    public void finish(Long reservationId) {
        Reservation r = getOwnReservation(reservationId);
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
            amount = powerUsed.multiply(pile.getPrice()).setScale(2, RoundingMode.HALF_UP);
        }
        r.setStatus("FINISHED");
        r.setEndTime(now);
        r.setDuration((int) minutes);
        r.setPowerUsed(powerUsed);
        r.setAmount(amount);
        reservationMapper.updateById(r);

        chargingPileMapper.updateStatus(r.getPileId(), "IDLE");
        chargingPileService.evictStationCache(r.getStationId());
    }

    @Override
    public void cancel(Long reservationId) {
        Reservation r = getOwnReservation(reservationId);
        if (!"PENDING".equals(r.getStatus())) {
            throw new BusinessException(ResultCode.RESERVATION_STATUS_ERROR);
        }
        r.setStatus("CANCELLED");
        reservationMapper.updateById(r);
        chargingPileMapper.updateStatus(r.getPileId(), "IDLE");
        chargingPileService.evictStationCache(r.getStationId());
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

    /** 基于 Redis 自增序列生成全局唯一订单号 */
    private String generateOrderNo() {
        Long seq = redisTemplate.opsForValue().increment("order:seq");
        if (seq == null) {
            seq = System.currentTimeMillis();
        }
        return "PQ" + LocalDateTime.now().format(ORDER_TIME_FMT) + String.format("%05d", seq % 100000);
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
            case "CHARGING" -> "充电中";
            case "FINISHED" -> "已完成";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }
}
