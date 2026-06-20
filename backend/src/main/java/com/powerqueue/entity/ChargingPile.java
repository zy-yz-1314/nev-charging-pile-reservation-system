package com.powerqueue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充电桩。
 * version 字段配合乐观锁,抢桩时防止超卖。
 */
@Data
@TableName("charging_pile")
public class ChargingPile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stationId;
    private String pileNo;

    /** FAST 快充 / SLOW 慢充 */
    private String type;

    private BigDecimal power;
    private BigDecimal price;

    /** IDLE / RESERVED / CHARGING / FAULT */
    private String status;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
