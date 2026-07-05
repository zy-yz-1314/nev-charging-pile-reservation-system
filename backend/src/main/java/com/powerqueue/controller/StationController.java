package com.powerqueue.controller;

import com.powerqueue.common.Result;
import com.powerqueue.service.ChargingPileService;
import com.powerqueue.service.ForecastService;
import com.powerqueue.service.PricingService;
import com.powerqueue.service.StationService;
import com.powerqueue.vo.ForecastVO;
import com.powerqueue.vo.PileVO;
import com.powerqueue.vo.StationLoadVO;
import com.powerqueue.vo.StationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 充电站 / 充电桩查询接口(车主端,需登录)。
 */
@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
public class StationController {

    private final StationService stationService;
    private final ChargingPileService chargingPileService;
    private final PricingService pricingService;
    private final ForecastService forecastService;

    /** 站点列表(支持关键字搜索) */
    @GetMapping
    public Result<List<StationVO>> list(@RequestParam(required = false) String keyword) {
        return Result.success(stationService.listStations(keyword));
    }

    /** 站点详情 */
    @GetMapping("/{id}")
    public Result<StationVO> detail(@PathVariable Long id) {
        return Result.success(stationService.getDetail(id));
    }

    /** 某站点充电桩实时状态(走 Redis 缓存) */
    @GetMapping("/{id}/piles")
    public Result<List<PileVO>> piles(@PathVariable("id") Long stationId) {
        return Result.success(chargingPileService.listByStation(stationId));
    }

    /** 站点实时负载:空闲率 / 三色档 / 负载系数(L2) */
    @GetMapping("/{id}/load")
    public Result<StationLoadVO> load(@PathVariable("id") Long stationId) {
        return Result.success(pricingService.stationLoad(stationId));
    }

    /** 站点未来 N 小时需求预测(三色 🟢🟡🔴,L2) */
    @GetMapping("/{id}/forecast")
    public Result<List<ForecastVO>> forecast(@PathVariable("id") Long stationId,
                                             @RequestParam(defaultValue = "8") int hours) {
        return Result.success(forecastService.forecastForStation(stationId, hours));
    }
}
