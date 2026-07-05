package com.powerqueue.service.impl;

import com.powerqueue.common.BusinessException;
import com.powerqueue.common.ResultCode;
import com.powerqueue.entity.ChargingPile;
import com.powerqueue.entity.Reservation;
import com.powerqueue.mapper.ChargingPileMapper;
import com.powerqueue.mapper.ReservationMapper;
import com.powerqueue.service.ChargingPileService;
import com.powerqueue.service.PileReservationSupport;
import com.powerqueue.service.QueueService;
import com.powerqueue.vo.QueueEstimateVO;
import com.powerqueue.ws.ChargingEventBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueServiceImpl implements QueueService {

    private static final String QUEUE_PREFIX = "queue:pile:";
    private static final String CONFIRM_PREFIX = "queue:confirm:";
    private static final int DEFAULT_AVG_DURATION_MIN = 30;
    private static final DateTimeFormatter ORDER_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RedisTemplate<String, Object> redisTemplate;
    private final ReservationMapper reservationMapper;
    private final ChargingPileMapper chargingPileMapper;
    private final ChargingPileService chargingPileService;
    private final PileReservationSupport pileReservationSupport;
    private final ApplicationEventPublisher eventPublisher;
    private final RedissonClient redissonClient;

    @Value("${powerqueue.queue.confirm-window-seconds:600}")
    private long confirmWindowSeconds;
    @Value("${powerqueue.queue.avg-duration-window-days:7}")
    private int avgDurationDays;
    @Value("${powerqueue.reservation.lock-wait-seconds:3}")
    private long lockWaitSeconds;

    @Override
    public QueueEstimateVO enqueue(Long userId, Long pileId) {
        ChargingPile pile = chargingPileService.getByIdOrThrow(pileId);
        String key = queueKey(pileId);
        String member = String.valueOf(userId);
        // score = 入队时间戳(秒);若已在队内则跳过(不插队)
        Long rank = redisTemplate.opsForZSet().rank(key, member);
        if (rank == null) {
            redisTemplate.opsForZSet().add(key, member, System.currentTimeMillis() / 1000.0);
            // === 落库:创建排队记录,用户可在「我的预约」看到 ===
            Reservation r = new Reservation();
            r.setOrderNo(generateOrderNo());
            r.setUserId(userId);
            r.setPileId(pileId);
            r.setStationId(pile.getStationId());
            r.setReserveTime(LocalDateTime.now());
            r.setStatus("QUEUED");
            r.setAmount(BigDecimal.ZERO);
            reservationMapper.insert(r);
        }
        return estimate(userId, pileId);
    }

    @Override
    public QueueEstimateVO estimate(Long userId, Long pileId) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        boolean pileBusy = pile != null && !"IDLE".equals(pile.getStatus());

        String key = queueKey(pileId);
        Long size = redisTemplate.opsForZSet().zCard(key);
        long total = size == null ? 0 : size;

        Long myRank = redisTemplate.opsForZSet().rank(key, String.valueOf(userId));
        boolean inQueue = myRank != null;
        // 前面人数 = 我的排名(0 起),但必须 +1 算上正在使用桩的人
        long ahead = inQueue ? myRank : total;
        if (pileBusy) {
            ahead += 1; // 正在充电/已预约的那个人也算等待
        }

        int avg = avgDurationMinutes(pileId);
        int wait = (int) (ahead * avg);

        QueueEstimateVO v = new QueueEstimateVO();
        v.setPileId(pileId);
        v.setAheadCount(ahead);
        v.setMyRank(inQueue ? myRank : -1L);
        v.setEstimateWaitMin(wait);
        v.setAvgDurationMin(avg);
        v.setQueueState(inConfirmWindow(userId) ? "WAITING_CONFIRM" : (inQueue ? "QUEUED" : null));
        return v;
    }

    @Override
    public int estimateWaitMinutes(Long pileId) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile != null && "IDLE".equals(pile.getStatus())) {
            return 0;
        }
        Long size = redisTemplate.opsForZSet().zCard(queueKey(pileId));
        long queueSize = size == null ? 0 : size;
        int avg = avgDurationMinutes(pileId);
        // +1 算上正在使用桩的那个人
        return (int) ((queueSize + 1) * avg);
    }

    @Override
    public long myRank(Long pileId, Long userId) {
        Long rank = redisTemplate.opsForZSet().rank(queueKey(pileId), String.valueOf(userId));
        return rank == null ? -1 : rank;
    }

    @Override
    public Long popHead(Long pileId) {
        ZSetOperations.TypedTuple<Object> head = redisTemplate.opsForZSet().popMin(queueKey(pileId));
        if (head == null || head.getValue() == null) {
            return null;
        }
        Long userId = Long.valueOf(head.getValue().toString());
        redisTemplate.opsForValue().set(confirmKey(userId), String.valueOf(pileId),
                confirmWindowSeconds, TimeUnit.SECONDS);
        // 更新排队记录状态为"待确认占位"
        Reservation r = reservationMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Reservation>()
                        .eq(Reservation::getUserId, userId)
                        .eq(Reservation::getPileId, pileId)
                        .eq(Reservation::getStatus, "QUEUED"));
        if (r != null) {
            r.setStatus("WAITING_CONFIRM");
            r.setReserveTime(LocalDateTime.now()); // 用于前端倒计时起点
            reservationMapper.updateById(r);
        }
        // 桩预留:标记为 RESERVED,防止队列外用户直接抢桩插队(C)
        chargingPileMapper.updateStatus(pileId, "RESERVED");
        eventPublisher.publishEvent(ChargingEventBroadcaster.queueTurn(
                userId, null, pileId, "您排队的充电桩已空出,请在 10 分钟内确认占位"));
        log.info("桩 {} 队首用户 {} 进入 10 分钟确认窗口", pileId, userId);
        return userId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long confirm(Long userId, Long pileId) {
        // 桩锁与 grabForUser 串行化,锁内重检防调度器并发竞态(HIGH-1/2 修复)
        RLock lock = redissonClient.getLock("lock:pile:" + pileId);
        boolean locked = false;
        try {
            locked = lock.tryLock(lockWaitSeconds, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ResultCode.GRAB_BUSY);
            }
            Object c = redisTemplate.opsForValue().get(confirmKey(userId));
            if (c == null) {
                throw new BusinessException(ResultCode.CONFIRM_TIMEOUT);
            }
            if (!String.valueOf(pileId).equals(String.valueOf(c))) {
                throw new BusinessException(ResultCode.NOT_YOUR_TURN);
            }
            // 锁内重检:调度器让位后订单已非 WAITING_CONFIRM,必须跳过
            Reservation r = reservationMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Reservation>()
                            .eq(Reservation::getUserId, userId)
                            .eq(Reservation::getPileId, pileId)
                            .eq(Reservation::getStatus, "WAITING_CONFIRM"));
            if (r == null) {
                throw new BusinessException(ResultCode.CONFIRM_TIMEOUT);
            }
            redisTemplate.delete(confirmKey(userId));
            redisTemplate.opsForZSet().remove(queueKey(pileId), String.valueOf(userId));
            r.setStatus("CANCELLED");
            reservationMapper.updateById(r);
            return pileReservationSupport.grabForUser(userId, pileId, true);
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
    public void leave(Long userId, Long pileId) {
        redisTemplate.opsForZSet().remove(queueKey(pileId), String.valueOf(userId));
        redisTemplate.delete(confirmKey(userId));
        // 取消排队记录
        Reservation r = reservationMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Reservation>()
                        .eq(Reservation::getUserId, userId)
                        .eq(Reservation::getPileId, pileId)
                        .in(Reservation::getStatus, "QUEUED", "WAITING_CONFIRM"));
        if (r != null) {
            boolean wasWaitingConfirm = "WAITING_CONFIRM".equals(r.getStatus());
            r.setStatus("CANCELLED");
            reservationMapper.updateById(r);
            // 若为待确认占位,释放为队首预留的桩,让下一位可获取(C)
            if (wasWaitingConfirm) {
                chargingPileMapper.updateStatus(pileId, "IDLE");
            }
        }
    }

    // ============ 私有方法 ============

    private boolean inConfirmWindow(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(confirmKey(userId)));
    }

    private int avgDurationMinutes(Long pileId) {
        Integer avg = reservationMapper.avgDurationMinutes(pileId, avgDurationDays);
        if (avg == null || avg <= 0) {
            return DEFAULT_AVG_DURATION_MIN;
        }
        return avg;
    }

    private String generateOrderNo() {
        Long seq = redisTemplate.opsForValue().increment("order:seq");
        if (seq == null) {
            seq = System.currentTimeMillis();
        }
        return "PQ" + LocalDateTime.now().format(ORDER_TIME_FMT) + String.format("%05d", seq % 100000);
    }

    private String queueKey(Long pileId) {
        return QUEUE_PREFIX + pileId;
    }

    private String confirmKey(Long userId) {
        return CONFIRM_PREFIX + userId;
    }
}
