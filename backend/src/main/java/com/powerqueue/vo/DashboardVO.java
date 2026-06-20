package com.powerqueue.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 管理后台数据看板。
 */
@Data
public class DashboardVO {

    /** 充电站总数 */
    private long stationCount;
    /** 充电桩总数 */
    private long pileCount;
    /** 用户总数 */
    private long userCount;

    /** 汇总指标 */
    private Summary summary;

    /** 充电桩各状态数量(IDLE/RESERVED/CHARGING/FAULT) */
    private Map<String, Long> pileStatusCount;

    /** 订单各状态数量 */
    private Map<String, Long> reservationStatusCount;

    /** 近 7 天营收趋势 */
    private List<TrendItem> revenueTrend;

    @Data
    public static class Summary {
        private Long totalOrders;
        private Long todayOrders;
        private BigDecimal totalRevenue;
        private BigDecimal todayRevenue;
    }

    @Data
    public static class TrendItem {
        private String date;
        private Long orders;
        private BigDecimal revenue;
    }
}
