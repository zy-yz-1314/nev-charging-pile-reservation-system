package com.powerqueue.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 站点实时负载(L2),供三色展示与负载系数计算共用。
 */
@Data
public class StationLoadVO {

    private Long stationId;
    private Integer idle;
    private Integer total;
    /** 空闲率% */
    private BigDecimal idleRate;
    /** GREEN 宽松 / YELLOW 较忙 / RED 高峰 */
    private String loadLevel;
    /** 负载系数(0.90/1.00/1.30) */
    private BigDecimal loadCoefficient;
}
