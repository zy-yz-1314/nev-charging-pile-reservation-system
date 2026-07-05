package com.powerqueue.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * 充电计划视图(L3)。
 */
@Data
public class ChargePlanVO {

    private Long id;
    private Long userId;
    private Long stationId;
    private String stationName;
    /** 周期日:1,3,5 */
    private String cronDays;
    private LocalTime chargeTime;
    private BigDecimal targetEnergy;
    private BigDecimal commuteKm;
    private Integer hasHomeCharger;
    private Integer enabled;
    /** 生成理由(可解释推荐) */
    private String reason;
}
