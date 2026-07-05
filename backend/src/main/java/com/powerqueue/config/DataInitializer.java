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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
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
    private final DataSource dataSource;

    @Value("${powerqueue.demo.default-password:powerqueue_demo_2026}")
    private String demoDefaultPassword;

    @Override
    public void run(String... args) {
        migrateV2();
        ensureIndexes();
        initUsers();
        initSampleReservations();
    }

    /**
     * 幂等补充索引:针对运行期新增的高频查询补建复合索引(独立于 V2 哨兵,每次启动都检查)。
     * 已存在的索引会被 MySQL 拒绝(重复键名),try-catch 忽略即幂等。
     */
    private void ensureIndexes() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            // L1 超时让位扫描:每分钟扫 WAITING_CONFIRM 超时单,需 (status, reserve_time) 复合索引避免全表扫
            try {
                stmt.execute("ALTER TABLE reservation ADD INDEX idx_status_reserve_time (status, reserve_time)");
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.warn("索引补充失败: {}", e.getMessage());
        }
    }

    /**
     * v2 四层智能化自动增量迁移:检测新表/新字段是否存在,不存在则执行 DDL + 种子数据。
     * 幂等:已存在的表/字段跳过,多次启动不会重复执行。
     */
    private void migrateV2() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {

            // --- 检测是否已迁移(以 score_weight_config 表为哨兵) ---
            boolean migrated = false;
            try (ResultSet rs = conn.getMetaData().getTables(null, null, "score_weight_config", null)) {
                migrated = rs.next();
            }
            if (migrated) {
                log.info("V2 增量迁移已存在,跳过");
                return;
            }
            log.info("开始执行 V2 四层智能化增量迁移...");

            // --- reservation 表扩展(L1/L2 动态定价快照 + 队列态) ---
            try { stmt.execute("ALTER TABLE reservation ADD COLUMN target_energy DECIMAL(8,2) DEFAULT NULL COMMENT '目标充电电量(度)' AFTER amount"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE reservation ADD COLUMN final_unit_price DECIMAL(6,2) DEFAULT NULL COMMENT '结算单价快照' AFTER target_energy"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE reservation ADD COLUMN time_coefficient DECIMAL(3,2) DEFAULT NULL COMMENT '时段系数' AFTER final_unit_price"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE reservation ADD COLUMN load_coefficient DECIMAL(3,2) DEFAULT NULL COMMENT '站点负载系数' AFTER time_coefficient"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE reservation ADD COLUMN queue_state VARCHAR(20) DEFAULT NULL COMMENT '队列态' AFTER load_coefficient"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE reservation ADD COLUMN queue_enter_time DATETIME DEFAULT NULL COMMENT '入队时间' AFTER queue_state"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE reservation ADD KEY idx_pile_queue (pile_id, queue_state)"); } catch (Exception ignored) {}

            // --- 打分权重配置(L1) ---
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS score_weight_config (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  profile VARCHAR(32) NOT NULL DEFAULT 'default' COMMENT '策略档案',
                  w_distance DECIMAL(4,2) NOT NULL DEFAULT 0.40,
                  w_wait DECIMAL(4,2) NOT NULL DEFAULT 0.25,
                  w_price DECIMAL(4,2) NOT NULL DEFAULT 0.15,
                  w_power DECIMAL(4,2) NOT NULL DEFAULT 0.20,
                  enabled TINYINT NOT NULL DEFAULT 1,
                  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_profile (profile)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能匹配打分权重配置'
                """);

            // --- 动态定价规则(L2) ---
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pricing_rule (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  rule_type VARCHAR(16) NOT NULL COMMENT 'TIME/LOAD',
                  segment_key VARCHAR(32) NOT NULL,
                  coefficient DECIMAL(4,2) NOT NULL,
                  time_start TIME DEFAULT NULL,
                  time_end TIME DEFAULT NULL,
                  idle_rate_min DECIMAL(5,2) DEFAULT NULL,
                  idle_rate_max DECIMAL(5,2) DEFAULT NULL,
                  enabled TINYINT NOT NULL DEFAULT 1,
                  PRIMARY KEY (id),
                  KEY idx_type (rule_type)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态定价规则'
                """);

            // --- 需求预测(L2) ---
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS demand_forecast (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  station_id BIGINT NOT NULL,
                  day_of_week TINYINT NOT NULL,
                  hour TINYINT NOT NULL,
                  occupancy_rate DECIMAL(5,2) NOT NULL,
                  load_level VARCHAR(8) NOT NULL,
                  sample_count INT NOT NULL DEFAULT 0,
                  forecast_date DATE NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_station_dow_hour (station_id, day_of_week, hour),
                  KEY idx_forecast_date (forecast_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求预测'
                """);

            // --- 充电计划(L3) ---
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS charge_plan (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  station_id BIGINT NOT NULL,
                  cron_days VARCHAR(32) NOT NULL,
                  charge_time TIME NOT NULL,
                  target_energy DECIMAL(8,2) DEFAULT NULL,
                  commute_km DECIMAL(6,1) DEFAULT NULL,
                  has_home_charger TINYINT NOT NULL DEFAULT 0,
                  enabled TINYINT NOT NULL DEFAULT 1,
                  last_fire_time DATETIME DEFAULT NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_user_plan (user_id, enabled),
                  KEY idx_fire (cron_days, charge_time, enabled)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充电计划'
                """);

            // --- LLM 对话日志(L4) ---
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ai_chat_log (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  session_id VARCHAR(64) NOT NULL,
                  user_query VARCHAR(500) NOT NULL,
                  extracted_slots JSON DEFAULT NULL,
                  recommend_snapshot JSON DEFAULT NULL,
                  ai_reply TEXT DEFAULT NULL,
                  degraded TINYINT NOT NULL DEFAULT 0,
                  latency_ms INT DEFAULT NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_user_session (user_id, session_id),
                  KEY idx_create_time (create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM对话日志'
                """);

            // --- 种子数据(L1/L2 配置) ---
            seed(stmt, "INSERT IGNORE INTO score_weight_config(profile,w_distance,w_wait,w_price,w_power) VALUES ('default',0.40,0.25,0.15,0.20)");
            seed(stmt, "INSERT IGNORE INTO score_weight_config(profile,w_distance,w_wait,w_price,w_power) VALUES ('urgent', 0.30,0.45,0.05,0.20)");
            seed(stmt, "INSERT IGNORE INTO score_weight_config(profile,w_distance,w_wait,w_price,w_power) VALUES ('economy',0.35,0.15,0.40,0.10)");

            seed(stmt, "INSERT IGNORE INTO pricing_rule(rule_type,segment_key,coefficient,time_start,time_end,idle_rate_min,idle_rate_max) VALUES ('TIME','VALLEY',0.70,'00:00:00','08:00:00',NULL,NULL)");
            seed(stmt, "INSERT IGNORE INTO pricing_rule(rule_type,segment_key,coefficient,time_start,time_end,idle_rate_min,idle_rate_max) VALUES ('TIME','FLAT',  1.00,'08:00:00','17:00:00',NULL,NULL)");
            seed(stmt, "INSERT IGNORE INTO pricing_rule(rule_type,segment_key,coefficient,time_start,time_end,idle_rate_min,idle_rate_max) VALUES ('TIME','PEAK',  1.50,'17:00:00','21:00:00',NULL,NULL)");
            seed(stmt, "INSERT IGNORE INTO pricing_rule(rule_type,segment_key,coefficient,time_start,time_end,idle_rate_min,idle_rate_max) VALUES ('TIME','EVE',   1.00,'21:00:00','23:59:59',NULL,NULL)");
            seed(stmt, "INSERT IGNORE INTO pricing_rule(rule_type,segment_key,coefficient,time_start,time_end,idle_rate_min,idle_rate_max) VALUES ('LOAD','HIGH',  0.90,NULL,NULL,50.00,100.00)");
            seed(stmt, "INSERT IGNORE INTO pricing_rule(rule_type,segment_key,coefficient,time_start,time_end,idle_rate_min,idle_rate_max) VALUES ('LOAD','MID',   1.00,NULL,NULL,20.00,50.00)");
            seed(stmt, "INSERT IGNORE INTO pricing_rule(rule_type,segment_key,coefficient,time_start,time_end,idle_rate_min,idle_rate_max) VALUES ('LOAD','LOW',   1.30,NULL,NULL,0.00,20.00)");

            log.info("V2 四层智能化增量迁移完成");
        } catch (Exception e) {
            log.error("V2 迁移失败,请手动执行 sql/powerqueue_v2_intelligent.sql: {}", e.getMessage(), e);
        }
    }

    private void seed(Statement stmt, String sql) {
        try { stmt.execute(sql); } catch (Exception ignored) {}
    }

    private void initUsers() {
        ensureUser("admin", "系统管理员", "ADMIN", null);
        ensureUser("user", "示例车主", "USER", "京A·DEMO");
    }

    private void ensureUser(String username, String nickname, String role, String carPlate) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (count != null && count > 0) {
            return;
        }
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(demoDefaultPassword));
        u.setNickname(nickname);
        u.setRole(role);
        u.setPhone("13800000000");
        u.setCarPlate(carPlate);
        u.setBalance(new BigDecimal("200.00"));
        u.setStatus(1);
        userMapper.insert(u);
        log.info("初始化账号: {} ({}) 密码见环境变量", username, role);
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
