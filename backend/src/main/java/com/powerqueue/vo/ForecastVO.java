package com.powerqueue.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 站点未来时段需求预测(L2)。
 */
@Data
public class ForecastVO {

    private Long stationId;
    /** 1=周一 ... 7=周日 */
    private Integer dayOfWeek;
    /** 0-23 */
    private Integer hour;
    /** 预测占用率% */
    private BigDecimal occupancyRate;
    /** GREEN 宽松 / YELLOW 较忙 / RED 高峰 */
    private String loadLevel;
}
