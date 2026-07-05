package com.powerqueue.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 动态定价试算结果(L2)。
 */
@Data
public class PriceCalcVO {

    private Long pileId;
    private Long stationId;
    /** 桩基础单价 */
    private BigDecimal basePrice;
    /** 时段系数(0.70/1.00/1.50) */
    private BigDecimal timeCoefficient;
    /** 站点负载系数(0.90/1.00/1.30) */
    private BigDecimal loadCoefficient;
    /** 最终单价 = basePrice × timeCoefficient × loadCoefficient */
    private BigDecimal finalPrice;
    /** 时段档:VALLEY/FLAT/PEAK/EVE */
    private String segment;
    /** 站点空闲率% */
    private BigDecimal idleRate;
}
