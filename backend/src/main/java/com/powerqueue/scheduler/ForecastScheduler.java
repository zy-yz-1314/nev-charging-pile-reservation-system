package com.powerqueue.scheduler;

import com.powerqueue.service.ForecastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 需求预测定时预计算(L2):每天凌晨重算「站点 × 星期 × 小时」占用率,
 * 结果落 demand_forecast,运行时直接读缓存/表,避免实时聚合。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForecastScheduler {

    private final ForecastService forecastService;

    @Scheduled(cron = "${powerqueue.forecast.cron}")
    public void recompute() {
        try {
            forecastService.recompute();
        } catch (Exception e) {
            log.error("需求预测定时任务失败: {}", e.getMessage(), e);
        }
    }
}
