package com.powerqueue.controller;

import com.powerqueue.common.Result;
import com.powerqueue.service.PricingService;
import com.powerqueue.vo.PriceCalcVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 动态定价接口(L2,车主端,需登录)。
 * 最终电价 = 基础电价 × 时段系数 × 站点负载系数。
 */
@RestController
@RequestMapping("/api/price")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;

    /** 动态定价试算(可指定时刻 at,留空取当前) */
    @GetMapping("/calc")
    public Result<PriceCalcVO> calc(@RequestParam Long pileId,
                                    @RequestParam(required = false) String at) {
        LocalDateTime moment = (at == null || at.isBlank()) ? null : LocalDateTime.parse(at);
        return Result.success(pricingService.calc(pileId, moment));
    }
}
