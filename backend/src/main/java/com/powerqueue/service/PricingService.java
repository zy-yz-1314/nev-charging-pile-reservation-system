package com.powerqueue.service;

import com.powerqueue.vo.PriceCalcVO;
import com.powerqueue.vo.StationLoadVO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 动态供需定价服务(L2)。
 * 最终电价 = 基础电价 × 时段系数 × 站点负载系数。
 */
public interface PricingService {

    /** 某桩当前动态最终单价(基础价 × 时段系数 × 负载系数) */
    BigDecimal finalUnitPrice(Long pileId);

    /** 指定时刻的定价试算(供 /api/price/calc 与 LLM 编排调用) */
    PriceCalcVO calc(Long pileId, LocalDateTime at);

    /** 站点实时负载:空闲率 / 三色档 / 负载系数 */
    StationLoadVO stationLoad(Long stationId);
}
