package com.powerqueue.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 智能匹配推荐结果项(L1)。
 * score 由四因子归一化后加权得出,各分项明细一并提供,便于前端展示与可解释推荐。
 */
@Data
public class PileScoreVO {

    private Long pileId;
    private Long stationId;
    private String stationName;
    private String pileNo;
    /** FAST / SLOW */
    private String type;
    private BigDecimal power;

    /** 桩基础单价(元/度) */
    private BigDecimal basePrice;
    /** 动态最终电价(基础价×时段系数×负载系数,L2) */
    private BigDecimal finalPrice;

    /** 真实行驶距离(km,经地图 API/默认 Haversine) */
    private BigDecimal distanceKm;
    /** 预估等待时间(分钟,IDLE 为 0) */
    private Integer waitMin;
    /** 功率匹配度 [0,1] */
    private BigDecimal powerMatch;

    /** 综合得分(归一化加权,越大越优) */
    private BigDecimal score;

    /** 各因子归一化得分明细(可解释性) */
    private BigDecimal distanceScore;
    private BigDecimal waitScore;
    private BigDecimal priceScore;
    private BigDecimal powerScore;
}
