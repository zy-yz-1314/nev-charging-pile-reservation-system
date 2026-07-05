-- =============================================================
--  PowerQueue v2 · 四层智能化增量脚本 (MySQL 8.0+)
--  在 powerqueue.sql 之后执行,不破坏已有表结构与并发模型
-- =============================================================
USE `powerqueue`;

-- -------------------------------------------------------------
-- 1. reservation 扩展:动态定价快照 + 目标电量 + 队列态
--    定价快照在「开始充电」时锁定,保护用户不被中途调价影响
-- -------------------------------------------------------------
ALTER TABLE `reservation`
  ADD COLUMN `target_energy`     DECIMAL(8,2) DEFAULT NULL COMMENT '目标充电电量(度,L1推荐入参)'                  AFTER `amount`,
  ADD COLUMN `final_unit_price`  DECIMAL(6,2) DEFAULT NULL COMMENT '结算单价(动态:基础价×时段系数×负载系数快照)'  AFTER `target_energy`,
  ADD COLUMN `time_coefficient`  DECIMAL(3,2) DEFAULT NULL COMMENT '下单时时段系数(0.70/1.00/1.50)'               AFTER `final_unit_price`,
  ADD COLUMN `load_coefficient`  DECIMAL(3,2) DEFAULT NULL COMMENT '下单时站点负载系数(0.90/1.00/1.30)'            AFTER `time_coefficient`,
  ADD COLUMN `queue_state`       VARCHAR(20)  DEFAULT NULL COMMENT '队列态:QUEUED/WAITING_CONFIRM/NULL'            AFTER `load_coefficient`,
  ADD COLUMN `queue_enter_time`  DATETIME     DEFAULT NULL COMMENT '入队时间(预估等待计算依据)'                    AFTER `queue_state`;

ALTER TABLE `reservation`
  ADD KEY `idx_pile_queue` (`pile_id`, `queue_state`) COMMENT '排队订单查询/队首轮换';

-- -------------------------------------------------------------
-- 2. 打分权重配置(后台可调,L1)
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `score_weight_config`;
CREATE TABLE `score_weight_config` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `profile`     VARCHAR(32)  NOT NULL DEFAULT 'default' COMMENT '策略档案:default/urgent/economy',
  `w_distance`  DECIMAL(4,2) NOT NULL DEFAULT 0.40 COMMENT '距离权重 w1',
  `w_wait`      DECIMAL(4,2) NOT NULL DEFAULT 0.25 COMMENT '等待时间权重 w2',
  `w_price`     DECIMAL(4,2) NOT NULL DEFAULT 0.15 COMMENT '价格权重 w3',
  `w_power`     DECIMAL(4,2) NOT NULL DEFAULT 0.20 COMMENT '功率匹配度权重 w4',
  `enabled`     TINYINT      NOT NULL DEFAULT 1,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_profile` (`profile`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能匹配打分权重配置';

INSERT INTO `score_weight_config`(`profile`,`w_distance`,`w_wait`,`w_price`,`w_power`) VALUES
('default', 0.40, 0.25, 0.15, 0.20),
('urgent',  0.30, 0.45, 0.05, 0.20),
('economy', 0.35, 0.15, 0.40, 0.10);

-- -------------------------------------------------------------
-- 3. 动态定价规则(L2)
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `pricing_rule`;
CREATE TABLE `pricing_rule` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `rule_type`     VARCHAR(16)  NOT NULL                COMMENT 'TIME时段 / LOAD负载',
  `segment_key`   VARCHAR(32)  NOT NULL                COMMENT '时段:VALLEY/FLAT/PEAK;负载:HIGH/MID/LOW',
  `coefficient`   DECIMAL(4,2) NOT NULL                COMMENT '系数',
  `time_start`    TIME         DEFAULT NULL            COMMENT '时段起(仅TIME)',
  `time_end`      TIME         DEFAULT NULL            COMMENT '时段止(仅TIME)',
  `idle_rate_min` DECIMAL(5,2) DEFAULT NULL            COMMENT '负载档下限%(仅LOAD)',
  `idle_rate_max` DECIMAL(5,2) DEFAULT NULL            COMMENT '负载档上限%(仅LOAD)',
  `enabled`       TINYINT      NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_type` (`rule_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态定价规则';

INSERT INTO `pricing_rule`(`rule_type`,`segment_key`,`coefficient`,`time_start`,`time_end`,`idle_rate_min`,`idle_rate_max`) VALUES
('TIME','VALLEY', 0.70, '00:00:00','08:00:00', NULL, NULL),
('TIME','FLAT',   1.00, '08:00:00','17:00:00', NULL, NULL),
('TIME','PEAK',   1.50, '17:00:00','21:00:00', NULL, NULL),
('TIME','EVE',    1.00, '21:00:00','23:59:59', NULL, NULL),
('LOAD','HIGH',   0.90, NULL, NULL, 50.00, 100.00),
('LOAD','MID',    1.00, NULL, NULL, 20.00,  50.00),
('LOAD','LOW',    1.30, NULL, NULL,  0.00,  20.00);

-- -------------------------------------------------------------
-- 4. 需求预测结果(L2)
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `demand_forecast`;
CREATE TABLE `demand_forecast` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `station_id`     BIGINT       NOT NULL,
  `day_of_week`    TINYINT      NOT NULL                COMMENT '1=周一...7=周日',
  `hour`           TINYINT      NOT NULL                COMMENT '0-23',
  `occupancy_rate` DECIMAL(5,2) NOT NULL                COMMENT '预测占用率%',
  `load_level`     VARCHAR(8)   NOT NULL                COMMENT 'GREEN/YELLOW/RED',
  `sample_count`   INT          NOT NULL DEFAULT 0,
  `forecast_date`  DATE         NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_station_dow_hour` (`station_id`,`day_of_week`,`hour`),
  KEY `idx_forecast_date` (`forecast_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分时段站点需求预测';

-- -------------------------------------------------------------
-- 5. 充电计划(L3 向导)
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `charge_plan`;
CREATE TABLE `charge_plan` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`          BIGINT       NOT NULL,
  `station_id`       BIGINT       NOT NULL,
  `cron_days`        VARCHAR(32)  NOT NULL                COMMENT '周期日:1,3,5',
  `charge_time`      TIME         NOT NULL,
  `target_energy`    DECIMAL(8,2) DEFAULT NULL,
  `commute_km`       DECIMAL(6,1) DEFAULT NULL,
  `has_home_charger` TINYINT      NOT NULL DEFAULT 0,
  `enabled`          TINYINT      NOT NULL DEFAULT 1,
  `last_fire_time`   DATETIME     DEFAULT NULL,
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_plan` (`user_id`,`enabled`),
  KEY `idx_fire` (`cron_days`,`charge_time`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充电计划(向导)';

-- -------------------------------------------------------------
-- 6. LLM 对话日志(L4)
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ai_chat_log`;
CREATE TABLE `ai_chat_log` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`           BIGINT       NOT NULL,
  `session_id`        VARCHAR(64)  NOT NULL,
  `user_query`        VARCHAR(500) NOT NULL                COMMENT '用户口语文本',
  `extracted_slots`   JSON         DEFAULT NULL            COMMENT '阶段一槽位提取',
  `recommend_snapshot` JSON        DEFAULT NULL            COMMENT '阶段二注入真实数据快照',
  `ai_reply`          TEXT         DEFAULT NULL,
  `degraded`          TINYINT      NOT NULL DEFAULT 0      COMMENT '是否降级',
  `latency_ms`        INT          DEFAULT NULL,
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_session` (`user_id`,`session_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM 助手对话日志';

-- =============================================================
--  完成。状态字段 status 为 VARCHAR,枚举扩展不改结构:
--    charging_pile.status  增加 QUEUED(排队中)
--    reservation.status    增加 QUEUED / WAITING_CONFIRM
-- =============================================================
