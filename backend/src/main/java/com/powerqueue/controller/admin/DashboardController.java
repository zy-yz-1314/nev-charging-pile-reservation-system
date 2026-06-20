package com.powerqueue.controller.admin;

import com.powerqueue.common.Result;
import com.powerqueue.service.admin.DashboardService;
import com.powerqueue.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台数据看板接口(需 ADMIN)。
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public Result<DashboardVO> dashboard() {
        return Result.success(dashboardService.getDashboard());
    }
}
