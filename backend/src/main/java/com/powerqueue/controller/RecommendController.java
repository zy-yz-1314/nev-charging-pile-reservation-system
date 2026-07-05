package com.powerqueue.controller;

import com.powerqueue.common.Result;
import com.powerqueue.dto.RecommendDTO;
import com.powerqueue.service.RecommendService;
import com.powerqueue.vo.PileScoreVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 智能匹配推荐接口(L1,车主端,需登录)。
 * APP 仅需「我要充电」(传位置/车型/目标电量),系统输出综合得分 Top-N 充电桩。
 */
@RestController
@RequestMapping("/api/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;

    /** 智能匹配 Top-N 充电桩 */
    @PostMapping
    public Result<List<PileScoreVO>> recommend(@RequestBody @Valid RecommendDTO dto) {
        return Result.success("智能匹配成功", recommendService.recommend(dto));
    }
}
