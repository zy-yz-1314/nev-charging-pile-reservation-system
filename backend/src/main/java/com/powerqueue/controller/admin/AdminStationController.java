package com.powerqueue.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerqueue.common.Result;
import com.powerqueue.entity.Station;
import com.powerqueue.service.admin.AdminStationService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 管理后台 - 充电站管理(需 ADMIN)。
 */
@RestController
@RequestMapping("/api/admin/stations")
@RequiredArgsConstructor
public class AdminStationController {

    private final AdminStationService stationService;

    @GetMapping
    public Result<IPage<Station>> page(@RequestParam(defaultValue = "1") long current,
                                       @RequestParam(defaultValue = "10") long size,
                                       @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Station> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.like(Station::getName, keyword);
        }
        qw.orderByDesc(Station::getId);
        return Result.success(stationService.page(new Page<>(current, size), qw));
    }

    @PostMapping
    public Result<Void> add(@RequestBody Station station) {
        stationService.save(station);
        return Result.success("新增成功", null);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Station station) {
        station.setId(id);
        stationService.updateById(station);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        stationService.removeById(id);
        return Result.success("删除成功", null);
    }
}
