package com.powerqueue.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 「我要充电」智能匹配入参(L1)。
 * 用户无需手动翻列表,系统据此输出综合得分 Top-N 充电桩。
 */
@Data
public class RecommendDTO {

    /** 用户实时经度 */
    @NotNull(message = "经度不能为空")
    private BigDecimal lng;

    /** 用户实时纬度 */
    @NotNull(message = "纬度不能为空")
    private BigDecimal lat;

    /** 车型支持的最大充电功率(kW)——用于功率匹配度计算 */
    @NotNull(message = "车型支持功率不能为空")
    @DecimalMin(value = "0.1", message = "功率必须大于 0")
    private BigDecimal carPowerKW;

    /** 电池容量(kWh)——用于估算充电时长 */
    private BigDecimal batteryCapacity;

    /** 目标充电电量(kWh)——用于估算充电时长 */
    private BigDecimal targetEnergy;

    /** 返回 Top-N,留空取默认配置 */
    private Integer topN;

    /** 策略档案:default / urgent(着急)/ economy(省钱) */
    private String profile;
}
