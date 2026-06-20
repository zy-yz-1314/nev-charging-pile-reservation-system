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

    /** PENDING / CHARGING / FINISHED / CANCELLED */
    private String status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
