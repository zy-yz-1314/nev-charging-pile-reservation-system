package com.powerqueue.service;

import com.powerqueue.dto.ChargePlanDTO;
import com.powerqueue.vo.ChargePlanVO;

import java.time.LocalTime;
import java.util.List;

/**
 * 充电计划向导服务(L3)。
 * 基于用户画像(通勤日/单程里程/是否有家充)+ 业务规则引擎生成周期充电计划,
 * 定时任务到点自动预约,失败则推送漏充提醒。
 */
public interface ChargePlanService {

    /** 根据画像生成充电计划 */
    ChargePlanVO generatePlan(ChargePlanDTO dto);

    /** 当前用户的计划列表 */
    List<ChargePlanVO> myPlans();

    /** 启停 / 修改充电时刻 */
    void update(Long planId, Integer enabled, LocalTime chargeTime);

    /** 删除计划 */
    void delete(Long planId);

    /** 触发到点计划(定时任务调用):自动预约,无空闲桩则漏充提醒 */
    void fireDuePlans();
}
