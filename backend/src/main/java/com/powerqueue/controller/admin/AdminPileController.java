package com.powerqueue.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerqueue.common.Result;
import com.powerqueue.entity.ChargingPile;
import com.powerqueue.service.ChargingPileService;
import com.powerqueue.service.admin.AdminPileService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 管理后台 - 充电桩管理(需 ADMIN)。
 * 增删改后失效对应站点缓存,保证车主端实时状态准确。
 */
@RestController
@RequestMapping("/api/admin/piles")
@RequiredArgsConstructor
public class AdminPileController {

    private final AdminPileService pileService;
    private final ChargingPileService chargingPileService;

    @GetMapping
    public Result<IPage<ChargingPile>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) Long stationId,
                                            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<ChargingPile> qw = new LambdaQueryWrapper<>();
        if (stationId != null) {
            qw.eq(ChargingPile::getStationId, stationId);
        }
        if (StringUtils.hasText(keyword)) {
            qw.like(ChargingPile::getPileNo, keyword);
        }
        qw.orderByDesc(ChargingPile::getId);
        return Result.success(pileService.page(new Page<>(current, size), qw));
    }

    @PostMapping
    public Result<Void> add(@RequestBody ChargingPile pile) {
        pileService.save(pile);
        chargingPileService.evictStationCache(pile.getStationId());
        return Result.success("新增成功", null);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ChargingPile pile) {
        pile.setId(id);
        pileService.updateById(pile);
        if (pile.getStationId() != null) {
            chargingPileService.evictStationCache(pile.getStationId());
        }
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ChargingPile pile = pileService.getById(id);
        pileService.removeById(id);
        if (pile != null) {
            chargingPileService.evictStationCache(pile.getStationId());
        }
        return Result.success("删除成功", null);
    }
}
