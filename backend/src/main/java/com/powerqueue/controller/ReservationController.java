package com.powerqueue.controller;

import com.powerqueue.common.Result;
import com.powerqueue.dto.ReserveDTO;
import com.powerqueue.service.ReservationService;
import com.powerqueue.vo.ReservationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 预约 / 抢桩接口(车主端,需登录)。
 */
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /** 抢桩 */
    @PostMapping
    public Result<Long> grab(@RequestBody @Valid ReserveDTO dto) {
        return Result.success("抢桩成功", reservationService.grabPile(dto.getPileId()));
    }

    /** 我的预约列表 */
    @GetMapping
    public Result<List<ReservationVO>> myList() {
        return Result.success(reservationService.myReservations());
    }

    /** 开始充电 */
    @PutMapping("/{id}/start")
    public Result<Void> start(@PathVariable Long id) {
        reservationService.startCharging(id);
        return Result.success("已开始充电", null);
    }

    /** 结束充电 */
    @PutMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id) {
        reservationService.finish(id);
        return Result.success("充电完成", null);
    }

    /** 取消预约 */
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        reservationService.cancel(id);
        return Result.success("已取消预约", null);
    }
}
