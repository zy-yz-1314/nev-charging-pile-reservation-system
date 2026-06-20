package com.powerqueue.service.admin.impl;

import com.powerqueue.entity.ChargingPile;
import com.powerqueue.mapper.ChargingPileMapper;
import com.powerqueue.mapper.ReservationMapper;
import com.powerqueue.mapper.StationMapper;
import com.powerqueue.mapper.UserMapper;
import com.powerqueue.service.admin.DashboardService;
import com.powerqueue.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final StationMapper stationMapper;
    private final ChargingPileMapper chargingPileMapper;
    private final UserMapper userMapper;
    private final ReservationMapper reservationMapper;

    @Override
    public DashboardVO getDashboard() {
        DashboardVO vo = new DashboardVO();
        vo.setStationCount(safeCount(stationMapper.selectCount(null)));
        vo.setPileCount(safeCount(chargingPileMapper.selectCount(null)));
        vo.setUserCount(safeCount(userMapper.selectCount(null)));

        // 充电桩状态分布(桩数量少,内存分组即可)
        Map<String, Long> pileStatus = new LinkedHashMap<>();
        for (ChargingPile p : chargingPileMapper.selectList(null)) {
            pileStatus.merge(p.getStatus(), 1L, Long::sum);
        }
        vo.setPileStatusCount(pileStatus);

        // 订单状态分布
        Map<String, Long> resStatus = new LinkedHashMap<>();
        for (Map<String, Object> m : reservationMapper.countByStatus()) {
            resStatus.put(String.valueOf(m.get("status")), toLong(m.get("cnt")));
        }
        vo.setReservationStatusCount(resStatus);

        // 汇总
        Map<String, Object> s = reservationMapper.summary();
        DashboardVO.Summary summary = new DashboardVO.Summary();
        if (s != null) {
            summary.setTotalOrders(toLong(s.get("totalOrders")));
            summary.setTodayOrders(toLong(s.get("todayOrders")));
            summary.setTotalRevenue(toBigDecimal(s.get("totalRevenue")));
            summary.setTodayRevenue(toBigDecimal(s.get("todayRevenue")));
        }
        vo.setSummary(summary);

        // 近 7 天营收趋势
        List<DashboardVO.TrendItem> trend = new ArrayList<>();
        for (Map<String, Object> m : reservationMapper.revenueTrend(7)) {
            DashboardVO.TrendItem t = new DashboardVO.TrendItem();
            t.setDate(String.valueOf(m.get("date")));
            t.setOrders(toLong(m.get("orders")));
            t.setRevenue(toBigDecimal(m.get("revenue")));
            trend.add(t);
        }
        vo.setRevenueTrend(trend);
        return vo;
    }

    private long safeCount(Long c) {
        return c == null ? 0L : c;
    }

    private Long toLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(o.toString());
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        if (o instanceof BigDecimal b) {
            return b;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(o.toString());
    }
}
