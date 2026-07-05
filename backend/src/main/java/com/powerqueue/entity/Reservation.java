package com.powerqueue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预约 / 充电订单。
 */
@Data
@TableName("reservation")
public class Reservation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long userId;
    private Long pileId;
    private Long stationId;

    private LocalDateTime reserveTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private Integer duration;
    private BigDecimal powerUsed;
    private BigDecimal amount;

    /** 目标充电电量(度),L1 智能匹配入参 */
    private BigDecimal targetEnergy;

    /** 结算单价:基础价 × 时段系数 × 负载系数(开始充电时锁定快照,L2 动态定价) */
    private BigDecimal finalUnitPrice;
    /** 下单/开充时时段系数(0.70/1.00/1.50) */
    private BigDecimal timeCoefficient;
    /** 下单/开充时站点负载系数(0.90/1.00/1.30) */
    private BigDecimal loadCoefficient;

    /** 队列态:QUEUED / WAITING_CONFIRM / null */
    private String queueState;
    /** 入队时间(预估等待计算依据) */
    private LocalDateTime queueEnterTime;

    /** PENDING / QUEUED / WAITING_CONFIRM / CHARGING / FINISHED / CANCELLED */
    private String status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
