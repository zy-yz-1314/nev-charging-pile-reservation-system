package com.powerqueue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * 动态定价规则(L2)。
 * rule_type=TIME 时段系数(VALLEY 0.7 / FLAT 1.0 / PEAK 1.5);
 * rule_type=LOAD 负载系数(空闲率>50% 0.9 引流,<20% 1.3 分流)。
 */
@Data
@TableName("pricing_rule")
public class PricingRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** TIME / LOAD */
    private String ruleType;
    /** VALLEY/FLAT/PEAK/EVE 或 HIGH/MID/LOW */
    private String segmentKey;
    private BigDecimal coefficient;
    private LocalTime timeStart;
    private LocalTime timeEnd;
    private BigDecimal idleRateMin;
    private BigDecimal idleRateMax;
    private Integer enabled;
}
