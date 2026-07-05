package com.powerqueue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 分时段站点需求预测(L2)。
 * 维度:站点 × 星期(1-7) × 小时(0-23),占用率由移动平均 + 季节性分解得出。
 */
@Data
@TableName("demand_forecast")
public class DemandForecast {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stationId;
    /** 1=周一 ... 7=周日 */
    private Integer dayOfWeek;
    /** 0-23 */
    private Integer hour;
    private BigDecimal occupancyRate;
    /** GREEN 宽松 / YELLOW 较忙 / RED 高峰 */
    private String loadLevel;
    private Integer sampleCount;
    private LocalDate forecastDate;
}
