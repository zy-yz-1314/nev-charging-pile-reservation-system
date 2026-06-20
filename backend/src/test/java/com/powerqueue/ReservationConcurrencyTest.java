package com.powerqueue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerqueue.common.UserContext;
import com.powerqueue.entity.ChargingPile;
import com.powerqueue.entity.Reservation;
import com.powerqueue.mapper.ChargingPileMapper;
import com.powerqueue.mapper.ReservationMapper;
import com.powerqueue.service.ReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 抢桩并发防超卖测试。
 * <p>
 * 模拟高并发黄金时段:N 个不同用户同时抢同一个空闲充电桩,
 * 断言最终【只有 1 个】成功、桩状态变为 RESERVED、且只生成 1 条订单。
 * <p>
 * 注意:这是集成测试,需要本机 MySQL 与 Redis 已启动。
 */
@SpringBootTest
class ReservationConcurrencyTest {

    @Autowired
    private ChargingPileMapper chargingPileMapper;
    @Autowired
    private ReservationMapper reservationMapper;
    @Autowired
    private ReservationService reservationService;

    private Long testPileId;

    @BeforeEach
    void setUp() {
        ChargingPile pile = new ChargingPile();
        pile.setStationId(999L); // 独立测试站点,避免污染演示数据
        pile.setPileNo("TEST-" + System.nanoTime());
        pile.setType("FAST");
        pile.setPower(new BigDecimal("120"));
        pile.setPrice(new BigDecimal("1.80"));
        pile.setStatus("IDLE");
        pile.setVersion(0);
        chargingPileMapper.insert(pile);
        testPileId = pile.getId();
    }

    @AfterEach
    void tearDown() {
        reservationMapper.delete(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getPileId, testPileId));
        chargingPileMapper.deleteById(testPileId);
    }

    @Test
    void onlyOneSucceedsWhenGrabbingConcurrently() throws InterruptedException {
        int threadCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final long uid = 1000 + i; // 每个线程模拟不同车主
            pool.submit(() -> {
                ready.countDown();
                try {
                    startGun.await(); // 所有线程就绪后同时发车
                    UserContext.set(new UserContext.CurrentUser(uid, "u" + uid, "USER"));
                    reservationService.grabPile(testPileId);
                    success.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    UserContext.clear();
                    done.countDown();
                }
            });
        }

        ready.await();
        startGun.countDown();
        done.await();
        pool.shutdown();

        // 核心断言:只有一个请求抢到
        assertEquals(1, success.get(), "并发抢同一充电桩,应只有 1 个成功");
        assertEquals(threadCount - 1, failed.get(), "其余请求都应失败");

        ChargingPile after = chargingPileMapper.selectById(testPileId);
        assertEquals("RESERVED", after.getStatus(), "桩最终应为已预约状态");

        Long orderCount = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getPileId, testPileId));
        assertEquals(1L, orderCount, "应只生成 1 条预约订单(无超卖)");
    }
}
