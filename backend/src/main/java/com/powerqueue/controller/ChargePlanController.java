package com.powerqueue.controller;

import com.powerqueue.common.Result;
import com.powerqueue.dto.ChargePlanDTO;
import com.powerqueue.service.ChargePlanService;
import com.powerqueue.vo.ChargePlanVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;

/**
 * 充电计划向导接口(L3,车主端,需登录)。
 */
@RestController
@RequestMapping("/api/charge-plans")
@RequiredArgsConstructor
public class ChargePlanController {

    private final ChargePlanService chargePlanService;

    /** 生成充电计划 */
    @PostMapping
    public Result<ChargePlanVO> generate(@RequestBody @Valid ChargePlanDTO dto) {
        return Result.success("计划已生成", chargePlanService.generatePlan(dto));
    }

    /** 我的计划列表 */
    @GetMapping
    public Result<List<ChargePlanVO>> myPlans() {
        return Result.success(chargePlanService.myPlans());
    }

    /** 启停 / 修改充电时刻 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @RequestParam Integer enabled,
                               @RequestParam(required = false) String chargeTime) {
        LocalTime time = (chargeTime == null || chargeTime.isBlank()) ? null : LocalTime.parse(chargeTime);
        chargePlanService.update(id, enabled, time);
        return Result.success("已更新", null);
    }

    /** 删除计划 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        chargePlanService.delete(id);
        return Result.success("已删除", null);
    }
}
