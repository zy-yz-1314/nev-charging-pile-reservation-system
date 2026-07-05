package com.powerqueue.scheduler;

import com.powerqueue.service.ChargePlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 充电计划定时任务(L3):周期扫描到点计划,自动预约充电;
 * 无空闲桩或预约失败时推送漏充提醒(经 WebSocket)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChargePlanScheduler {

    private final ChargePlanService chargePlanService;

    @Scheduled(cron = "${powerqueue.charge-plan.cron}")
    public void fireDuePlans() {
        try {
            chargePlanService.fireDuePlans();
        } catch (Exception e) {
            log.error("充电计划定时任务失败: {}", e.getMessage(), e);
        }
    }
}
