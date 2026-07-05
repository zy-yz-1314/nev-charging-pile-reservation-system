package com.powerqueue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerqueue.entity.ChargingPile;
import com.powerqueue.entity.DemandForecast;
import com.powerqueue.mapper.ChargingPileMapper;
import com.powerqueue.mapper.DemandForecastMapper;
import com.powerqueue.mapper.ReservationMapper;
import com.powerqueue.service.ForecastService;
import com.powerqueue.vo.ForecastVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 需求预测实现(L2):移动平均 + 季节性分解。
 *
 * <p>移动平均:对每个 (站点, 星期, 小时) 槽位,取近 N 周历史订单的「该槽位平均启动数」;
 * 季节性:按星期(周一早高峰 vs 周末)+ 小时分组,天然体现周期性。
 * 无深度学习,工程轻量、可解释、可维护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastServiceImpl implements ForecastService {

    private final ReservationMapper reservationMapper;
    private final ChargingPileMapper chargingPileMapper;
    private final DemandForecastMapper demandForecastMapper;

    @Value("${powerqueue.forecast.moving-window-weeks:4}")
    private int windowWeeks;
    @Value("${powerqueue.forecast.green-threshold:50}")
    private int greenThreshold;
    @Value("${powerqueue.forecast.red-threshold:80}")
    private int redThreshold;

    @Override
    public void recompute() {
        List<Map<String, Object>> samples = reservationMapper.hourlyLoadSamples(windowWeeks);
        if (samples.isEmpty()) {
            log.info("需求预测:无历史样本,跳过(需先产生已完成订单)");
            return;
        }
        Map<Long, Integer> totalPiles = totalPilesPerStation();

        // 清空旧预测,重新落库(每日凌晨一次,数据量可控)
        demandForecastMapper.delete(null);

        LocalDate today = LocalDate.now();
        int inserted = 0;
        for (Map<String, Object> row : samples) {
            Long stationId = toLong(row.get("stationId"));
            int dow = toInt(row.get("dow"));
            int hr = toInt(row.get("hr"));
            long cnt = toLong(row.get("cnt"));
            long distinctDays = toLong(row.get("distinctDays"));
            if (distinctDays == 0) {
                continue;
            }
            int total = totalPiles.getOrDefault(stationId, 0);
            if (total == 0) {
                continue;
            }
            // 移动平均占用率 = 周期内平均启动数 / 总桩数 × 100,封顶 100
            double avgStarts = (double) cnt / distinctDays;
            double occ = Math.min(100.0, avgStarts / total * 100.0);

            DemandForecast df = new DemandForecast();
            df.setStationId(stationId);
            df.setDayOfWeek(dow);
            df.setHour(hr);
            df.setOccupancyRate(BigDecimal.valueOf(occ).setScale(2, RoundingMode.HALF_UP));
            df.setLoadLevel(levelOf(occ));
            df.setSampleCount((int) cnt);
            df.setForecastDate(today);
            demandForecastMapper.insert(df);
            inserted++;
        }
        log.info("需求预测计算完成,写入 {} 条槽位预测", inserted);
    }

    @Override
    public List<ForecastVO> forecastForStation(Long stationId, int hoursAhead) {
        int span = Math.max(1, hoursAhead);
        LocalDateTime now = LocalDateTime.now();
        List<ForecastVO> out = new ArrayList<>(span);
        for (int i = 0; i < span; i++) {
            LocalDateTime t = now.plusHours(i);
            int dow = t.getDayOfWeek().getValue(); // ISO 1=Monday
            int hr = t.getHour();
            DemandForecast df = demandForecastMapper.selectOne(
                    new LambdaQueryWrapper<DemandForecast>()
                            .eq(DemandForecast::getStationId, stationId)
                            .eq(DemandForecast::getDayOfWeek, dow)
                            .eq(DemandForecast::getHour, hr));
            ForecastVO v = new ForecastVO();
            v.setStationId(stationId);
            v.setDayOfWeek(dow);
            v.setHour(hr);
            if (df != null) {
                v.setOccupancyRate(df.getOccupancyRate());
                v.setLoadLevel(df.getLoadLevel());
            } else {
                v.setOccupancyRate(BigDecimal.ZERO);
                v.setLoadLevel("GREEN"); // 无历史数据按宽松处理
            }
            out.add(v);
        }
        return out;
    }

    private Map<Long, Integer> totalPilesPerStation() {
        Map<Long, Integer> map = new HashMap<>();
        List<ChargingPile> piles = chargingPileMapper.selectList(
                new LambdaQueryWrapper<ChargingPile>().ne(ChargingPile::getStatus, "FAULT"));
        for (ChargingPile p : piles) {
            map.merge(p.getStationId(), 1, Integer::sum);
        }
        return map;
    }

    private String levelOf(double occupancy) {
        if (occupancy >= redThreshold) {
            return "RED";
        }
        if (occupancy >= greenThreshold) {
            return "YELLOW";
        }
        return "GREEN";
    }

    private static Long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static int toInt(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }
}
