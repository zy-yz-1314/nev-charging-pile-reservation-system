package com.powerqueue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 充电计划(L3 向导)。
 * 基于用户画像(通勤日/单程里程/是否有家充)+ 规则引擎生成,定时任务到点自动预约。
 */
@Data
@TableName("charge_plan")
public class ChargePlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long stationId;
    /** 周期日:1,3,5(周一三五) */
    private String cronDays;
    private LocalTime chargeTime;
    private BigDecimal targetEnergy;
    private BigDecimal commuteKm;
    /** 1 有家充 / 0 无 */
    private Integer hasHomeCharger;
    private Integer enabled;
    private LocalDateTime lastFireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
