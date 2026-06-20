package com.powerqueue.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerqueue.entity.ChargingPile;
import com.powerqueue.entity.Reservation;
import com.powerqueue.entity.User;
import com.powerqueue.mapper.ChargingPileMapper;
import com.powerqueue.mapper.ReservationMapper;
import com.powerqueue.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 启动时初始化数据:
 * - 创建默认账号 admin / user(密码用 BCrypt 加密,保证可登录);
 * - 若订单表为空,生成若干历史已完成订单,供看板演示。
 * 幂等:已存在则跳过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final ReservationMapper reservationMapper;
    private final ChargingPileMapper chargingPileMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        initUsers();
        initSampleReservations();
    }

    private void initUsers() {
        ensureUser("admin", "系统管理员", "ADMIN", null);
        ensureUser("user", "示例车主", "USER", "京A·12345");
    }

    private void ensureUser(String username, String nickname, String role, String carPlate) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (count != null && count > 0) {
            return;
        }
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode("123456"));
        u.setNickname(nickname);
        u.setRole(role);
        u.setPhone("13800001234");
        u.setCarPlate(carPlate);
        u.setBalance(new BigDecimal("200.00"));
        u.setStatus(1);
        userMapper.insert(u);
        log.info("初始化账号: {} / 123456 ({})", username, role);
    }

    private void initSampleReservations() {
        Long existing = reservationMapper.selectCount(null);
        if (existing != null && existing > 0) {
            return;
        }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, "user"));
        if (user == null) {
            return;
        }
        List<ChargingPile> piles = chargingPileMapper.selectList(
                new LambdaQueryWrapper<ChargingPile>().last("LIMIT 8"));
        if (piles.isEmpty()) {
            return;
        }
        // 生成 8 条历史已完成订单,分布在过去 7 天,供看板趋势图展示
        for (int i = 0; i < 8; i++) {
            ChargingPile pile = piles.get(i % piles.size());
            LocalDateTime start = LocalDateTime.now().minusDays(i).minusHours(2);
            LocalDateTime end = start.plusMinutes(45 + i * 5L);
            BigDecimal power = new BigDecimal(20 + i * 3);
            BigDecimal amount = power.multiply(pile.getPrice()).setScale(2, RoundingMode.HALF_UP);

            Reservation r = new Reservation();
            r.setOrderNo("PQ" + (20260400000L + i));
            r.setUserId(user.getId());
            r.setPileId(pile.getId());
            r.setStationId(pile.getStationId());
            r.setReserveTime(start.minusMinutes(10));
            r.setStartTime(start);
            r.setEndTime(end);
            r.setDuration((int) Duration.between(start, end).toMinutes());
            r.setPowerUsed(power);
            r.setAmount(amount);
            r.setStatus("FINISHED");
            r.setCreateTime(start.minusMinutes(10));
            reservationMapper.insert(r);
        }
        log.info("初始化示例订单完成,共 8 条");
    }
}
