package com.powerqueue.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.powerqueue.common.Result;
import com.powerqueue.service.admin.AdminReservationService;
import com.powerqueue.vo.ReservationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 订单管理(需 ADMIN)。
 */
@RestController
@RequestMapping("/api/admin/reservations")
@RequiredArgsConstructor
public class AdminReservationController {

    private final AdminReservationService reservationService;

    @GetMapping
    public Result<IPage<ReservationVO>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String keyword) {
        return Result.success(reservationService.page(current, size, status, keyword));
    }
}
