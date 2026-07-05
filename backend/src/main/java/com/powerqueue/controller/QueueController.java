package com.powerqueue.controller;

import com.powerqueue.common.Result;
import com.powerqueue.common.UserContext;
import com.powerqueue.dto.EnqueueDTO;
import com.powerqueue.service.QueueService;
import com.powerqueue.vo.QueueEstimateVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 充电桩等待队列接口(L1,车主端,需登录)。
 * 满桩时用户进入对应桩等待队列,系统预估等待时间,轮到后 10 分钟内确认占位。
 */
@RestController
@RequestMapping("/api/queues")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    /** 进入等待队列 */
    @PostMapping
    public Result<QueueEstimateVO> enqueue(@RequestBody @Valid EnqueueDTO dto) {
        return Result.success("已加入等待队列", queueService.enqueue(UserContext.getUserId(), dto.getPileId()));
    }

    /** 查询某桩排队预估(等待人数 / 预估时间 / 我的排名) */
    @GetMapping("/{pileId}/estimate")
    public Result<QueueEstimateVO> estimate(@PathVariable Long pileId) {
        return Result.success(queueService.estimate(UserContext.getUserId(), pileId));
    }

    /** 轮到后确认占位(10 分钟窗口内) */
    @PostMapping("/{pileId}/confirm")
    public Result<Long> confirm(@PathVariable Long pileId) {
        return Result.success("占位成功", queueService.confirm(UserContext.getUserId(), pileId));
    }

    /** 退出排队 */
    @DeleteMapping("/{pileId}")
    public Result<Void> leave(@PathVariable Long pileId) {
        queueService.leave(UserContext.getUserId(), pileId);
        return Result.success("已退出排队", null);
    }
}
