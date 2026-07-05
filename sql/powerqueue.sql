-- =============================================================
--  PowerQueue · 新能源汽车充电桩高并发预约与调度系统
--  数据库初始化脚本 (MySQL 8.0+)
-- -------------------------------------------------------------
--  使用方式:
--    mysql -u root -p < powerqueue.sql
--    或在 Navicat / DataGrip 中直接运行本脚本
--
--  说明:
--    本脚本负责【建库 + 建表 + 索引 + 充电站/充电桩基础数据】。
--    用户账号(admin / user)与示例订单由后端首次启动时
--    DataInitializer 自动创建,以保证 BCrypt 密码加密正确。
-- =============================================================

CREATE DATABASE IF NOT EXISTS `powerqueue`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `powerqueue`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------------------------------------------------
-- 1. 用户表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username`    VARCHAR(50)   NOT NULL                COMMENT '用户名(登录账号)',
  `password`    VARCHAR(100)  NOT NULL                COMMENT '密码(BCrypt 加密)',
  `nickname`    VARCHAR(50)   DEFAULT NULL            COMMENT '昵称',
  `phone`       VARCHAR(20)   DEFAULT NULL            COMMENT '手机号',
  `car_plate`   VARCHAR(20)   DEFAULT NULL            COMMENT '车牌号',
  `role`        VARCHAR(20)   NOT NULL DEFAULT 'USER' COMMENT '角色:USER 车主 / ADMIN 管理员',
  `balance`     DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '账户余额(元)',
  `avatar`      VARCHAR(255)  DEFAULT NULL            COMMENT '头像URL',
  `status`      TINYINT       NOT NULL DEFAULT 1      COMMENT '状态:1 正常 / 0 禁用',
  `deleted`     TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除:0 未删 / 1 已删',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP                  COMMENT '创建时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- -------------------------------------------------------------
-- 2. 充电站表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `station`;
CREATE TABLE `station` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '充电站ID',
  `name`        VARCHAR(100)  NOT NULL                COMMENT '站点名称',
  `address`     VARCHAR(255)  DEFAULT NULL            COMMENT '详细地址',
  `longitude`   DECIMAL(10,6) DEFAULT NULL            COMMENT '经度',
  `latitude`    DECIMAL(10,6) DEFAULT NULL            COMMENT '纬度',
  `cover`       VARCHAR(255)  DEFAULT NULL            COMMENT '封面图URL',
  `description` VARCHAR(500)  DEFAULT NULL            COMMENT '站点描述',
  `status`      TINYINT       NOT NULL DEFAULT 1      COMMENT '状态:1 营业 / 0 停业',
  `deleted`     TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP                  COMMENT '创建时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`)               COMMENT '站点名称检索'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '充电站表';

-- -------------------------------------------------------------
-- 3. 充电桩表
--    version:乐观锁版本号,抢桩时配合 UPDATE...WHERE 防超卖
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `charging_pile`;
CREATE TABLE `charging_pile` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '充电桩ID',
  `station_id`  BIGINT        NOT NULL                COMMENT '所属充电站ID',
  `pile_no`     VARCHAR(50)   NOT NULL                COMMENT '充电桩编号',
  `type`        VARCHAR(20)   NOT NULL DEFAULT 'FAST' COMMENT '类型:FAST 快充 / SLOW 慢充',
  `power`       DECIMAL(6,2)  NOT NULL DEFAULT 0      COMMENT '额定功率(kW)',
  `price`       DECIMAL(6,2)  NOT NULL DEFAULT 0      COMMENT '充电单价(元/度)',
  `status`      VARCHAR(20)   NOT NULL DEFAULT 'IDLE' COMMENT '状态:IDLE 空闲 / RESERVED 已预约 / CHARGING 充电中 / FAULT 故障',
  `version`     INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  `deleted`     TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP                  COMMENT '创建时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_station_status` (`station_id`, `status`) COMMENT '站点下按状态查空闲桩(抢桩高频查询)',
  KEY `idx_pile_no` (`pile_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '充电桩表';

-- -------------------------------------------------------------
-- 4. 预约/充电订单表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `reservation`;
CREATE TABLE `reservation` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no`    VARCHAR(32)   NOT NULL                COMMENT '订单编号',
  `user_id`     BIGINT        NOT NULL                COMMENT '用户ID',
  `pile_id`     BIGINT        NOT NULL                COMMENT '充电桩ID',
  `station_id`  BIGINT        NOT NULL                COMMENT '充电站ID',
  `reserve_time`DATETIME      DEFAULT NULL            COMMENT '预约/下单时间',
  `start_time`  DATETIME      DEFAULT NULL            COMMENT '开始充电时间',
  `end_time`    DATETIME      DEFAULT NULL            COMMENT '结束充电时间',
  `duration`    INT           DEFAULT 0               COMMENT '充电时长(分钟)',
  `power_used`  DECIMAL(8,2)  DEFAULT 0               COMMENT '充电电量(度)',
  `amount`      DECIMAL(10,2) NOT NULL DEFAULT 0      COMMENT '订单金额(元)',
  `status`      VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '状态:PENDING 待充电 / CHARGING 充电中 / FINISHED 已完成 / CANCELLED 已取消',
  `deleted`     TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP                  COMMENT '创建时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_status` (`user_id`, `status`)  COMMENT '我的预约列表查询',
  KEY `idx_pile_status` (`pile_id`, `status`)  COMMENT '充电桩占用查询/防重复占用',
  KEY `idx_create_time` (`create_time`)        COMMENT '看板按时间统计'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '预约/充电订单表';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
--  初始数据:充电站
-- =============================================================
INSERT INTO `station` (`id`, `name`, `address`, `longitude`, `latitude`, `description`, `status`) VALUES
(1, '国贸中心充电站',   '北京市朝阳区建国门外大街1号',   116.461000, 39.909200, '位于CBD核心区,8个充电车位,7×24小时营业', 1),
(2, '中关村科技充电站', '北京市海淀区中关村大街27号',     116.316000, 39.983000, '紧邻地铁口,快充为主,周边配套齐全',       1),
(3, '望京SOHO充电站',   '北京市朝阳区望京街10号',         116.473000, 40.003000, '地下停车场内,环境舒适,支持预约',         1),
(4, '亦庄经开充电站',   '北京市大兴区荣华中路8号',         116.506000, 39.795000, '大型充电广场,车位充足,夜间优惠',         1),
(5, '西二旗地铁充电站', '北京市海淀区西二旗大街19号',     116.305000, 40.052000, '科技园区配套,通勤首选,极速快充',         1);

-- =============================================================
--  初始数据:充电桩(每站 8 个,共 40 个)
--  约定:前 4 个为快充(FAST),后 4 个为慢充(SLOW)
--  站点状态分布有意混合,便于演示不同状态的实时展示
-- =============================================================
INSERT INTO `charging_pile` (`station_id`, `pile_no`, `type`, `power`, `price`, `status`) VALUES
-- 站点 1 · 国贸中心
(1, 'A-01', 'FAST', 120.00, 1.80, 'IDLE'),
(1, 'A-02', 'FAST', 120.00, 1.80, 'CHARGING'),
(1, 'A-03', 'FAST', 60.00,  1.60, 'IDLE'),
(1, 'A-04', 'FAST', 60.00,  1.60, 'RESERVED'),
(1, 'A-05', 'SLOW', 7.00,   1.00, 'IDLE'),
(1, 'A-06', 'SLOW', 7.00,   1.00, 'IDLE'),
(1, 'A-07', 'SLOW', 7.00,   1.00, 'CHARGING'),
(1, 'A-08', 'SLOW', 7.00,   1.00, 'FAULT'),
-- 站点 2 · 中关村
(2, 'B-01', 'FAST', 120.00, 1.85, 'IDLE'),
(2, 'B-02', 'FAST', 120.00, 1.85, 'IDLE'),
(2, 'B-03', 'FAST', 120.00, 1.85, 'CHARGING'),
(2, 'B-04', 'FAST', 60.00,  1.55, 'IDLE'),
(2, 'B-05', 'SLOW', 7.00,   1.05, 'IDLE'),
(2, 'B-06', 'SLOW', 7.00,   1.05, 'RESERVED'),
(2, 'B-07', 'SLOW', 7.00,   1.05, 'IDLE'),
(2, 'B-08', 'SLOW', 7.00,   1.05, 'IDLE'),
-- 站点 3 · 望京SOHO
(3, 'C-01', 'FAST', 180.00, 2.00, 'IDLE'),
(3, 'C-02', 'FAST', 180.00, 2.00, 'CHARGING'),
(3, 'C-03', 'FAST', 120.00, 1.80, 'IDLE'),
(3, 'C-04', 'FAST', 120.00, 1.80, 'IDLE'),
(3, 'C-05', 'SLOW', 7.00,   1.10, 'IDLE'),
(3, 'C-06', 'SLOW', 7.00,   1.10, 'CHARGING'),
(3, 'C-07', 'SLOW', 7.00,   1.10, 'IDLE'),
(3, 'C-08', 'SLOW', 7.00,   1.10, 'IDLE'),
-- 站点 4 · 亦庄经开
(4, 'D-01', 'FAST', 120.00, 1.70, 'IDLE'),
(4, 'D-02', 'FAST', 120.00, 1.70, 'IDLE'),
(4, 'D-03', 'FAST', 60.00,  1.50, 'IDLE'),
(4, 'D-04', 'FAST', 60.00,  1.50, 'CHARGING'),
(4, 'D-05', 'SLOW', 7.00,   0.95, 'IDLE'),
(4, 'D-06', 'SLOW', 7.00,   0.95, 'IDLE'),
(4, 'D-07', 'SLOW', 7.00,   0.95, 'IDLE'),
(4, 'D-08', 'SLOW', 7.00,   0.95, 'RESERVED'),
-- 站点 5 · 西二旗
(5, 'E-01', 'FAST', 180.00, 1.95, 'IDLE'),
(5, 'E-02', 'FAST', 180.00, 1.95, 'IDLE'),
(5, 'E-03', 'FAST', 120.00, 1.75, 'CHARGING'),
(5, 'E-04', 'FAST', 120.00, 1.75, 'IDLE'),
(5, 'E-05', 'SLOW', 7.00,   1.00, 'IDLE'),
(5, 'E-06', 'SLOW', 7.00,   1.00, 'IDLE'),
(5, 'E-07', 'SLOW', 7.00,   1.00, 'FAULT'),
(5, 'E-08', 'SLOW', 7.00,   1.00, 'IDLE');

-- =============================================================
--  完成。用户账号与示例订单将在后端启动时自动初始化:
--    演示账号密码由环境变量 POWERQUEUE_DEMO_PASSWORD 指定, 默认值见 application.yml
--    管理员: admin  / 车主: user
-- =============================================================
