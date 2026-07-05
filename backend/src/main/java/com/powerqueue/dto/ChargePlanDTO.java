package com.powerqueue.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 充电计划向导入参(L3)。
 * 用户输入示例:每周一三五通勤,单程 30km,无家用充电桩。
 */
@Data
public class ChargePlanDTO {

    /** 通勤日(ISO 1=周一..7=周日),如 [1,3,5] */
    @NotEmpty(message = "通勤日不能为空")
    private List<Integer> commuteDays;

    /** 单程里程(km) */
    @NotNull(message = "单程里程不能为空")
    private BigDecimal commuteKm;

    /** 是否有家用充电桩 */
    private Boolean hasHomeCharger;

    /** 车型百公里电耗(kWh/100km),默认按 15 估算 */
    private BigDecimal consumptionKwhPer100km;

    /** 用户位置(用于推荐固定充电站) */
    private BigDecimal lng;
    private BigDecimal lat;
}
