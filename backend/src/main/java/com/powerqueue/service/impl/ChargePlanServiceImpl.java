package com.powerqueue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerqueue.common.BusinessException;
import com.powerqueue.common.ResultCode;
import com.powerqueue.common.UserContext;
import com.powerqueue.dto.ChargePlanDTO;
import com.powerqueue.entity.ChargePlan;
import com.powerqueue.entity.ChargingPile;
import com.powerqueue.entity.Station;
import com.powerqueue.mapper.ChargePlanMapper;
import com.powerqueue.mapper.ChargingPileMapper;
import com.powerqueue.mapper.StationMapper;
import com.powerqueue.service.ChargePlanService;
import com.powerqueue.service.MapDistanceProvider;
import com.powerqueue.service.ReservationService;
import com.powerqueue.vo.ChargePlanVO;
import com.powerqueue.ws.ChargingEventBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 充电计划向导实现(L3):用户画像 + 业务规则引擎 + 定时自动预约。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargePlanServiceImpl implements ChargePlanService {

    /** 默认百公里电耗(kWh/100km) */
    private static final BigDecimal DEFAULT_CONSUMPTION = new BigDecimal("15");
    /** 单次充电覆盖天数(估算目标电量) */
    private static final int COVER_DAYS = 3;
    /** 计划默认充电时刻:谷时 06:00(电价最优惠) */
    private static final LocalTime DEFAULT_CHARGE_TIME = LocalTime.of(6, 0);
    /** 到点后可触发的窗口(小时) */
    private static final int FIRE_WINDOW_HOURS = 1;

    private final ChargePlanMapper chargePlanMapper;
    private final StationMapper stationMapper;
    private final ChargingPileMapper chargingPileMapper;
    private final MapDistanceProvider mapDistanceProvider;
    private final ReservationService reservationService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public ChargePlanVO generatePlan(ChargePlanDTO dto) {
        Long userId = UserContext.getUserId();

        // 规则一:有家充 → 不建议公共桩计划
        if (Boolean.TRUE.equals(dto.getHasHomeCharger())) {
            ChargePlanVO vo = new ChargePlanVO();
            vo.setUserId(userId);
            vo.setHasHomeCharger(1);
            vo.setEnabled(0);
            vo.setReason("检测到您有家用充电桩,建议家用充电,无需生成公共桩计划");
            return vo;
        }

        Station station = nearestOpenStation(dto.getLng(), dto.getLat());
        if (station == null) {
            throw new BusinessException(ResultCode.STATION_NOT_FOUND);
        }

        String cronDays = dto.getCommuteDays().stream()
                .sorted().map(String::valueOf).collect(Collectors.joining(","));

        BigDecimal consumption = dto.getConsumptionKwhPer100km() != null
                ? dto.getConsumptionKwhPer100km() : DEFAULT_CONSUMPTION;
        // 目标电量 ≈ 单程往返日耗电 × 覆盖天数
        BigDecimal dailyEnergy = dto.getCommuteKm()
                .multiply(BigDecimal.valueOf(2))
                .multiply(consumption)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal targetEnergy = dailyEnergy.multiply(BigDecimal.valueOf(COVER_DAYS))
                .setScale(1, RoundingMode.HALF_UP);

        ChargePlan plan = new ChargePlan();
        plan.setUserId(userId);
        plan.setStationId(station.getId());
        plan.setCronDays(cronDays);
        plan.setChargeTime(DEFAULT_CHARGE_TIME);
        plan.setTargetEnergy(targetEnergy);
        plan.setCommuteKm(dto.getCommuteKm());
        plan.setHasHomeCharger(0);
        plan.setEnabled(1);
        chargePlanMapper.insert(plan);

        return toVO(plan, station.getName(),
                "已为您选定最近的「" + station.getName() + "」作为固定站点,每周 " + cronDays
                        + " 的 " + DEFAULT_CHARGE_TIME + " 自动预约(谷时电价更优惠),目标补能 "
                        + targetEnergy + " 度");
    }

    @Override
    public List<ChargePlanVO> myPlans() {
        Long userId = UserContext.getUserId();
        List<ChargePlan> plans = chargePlanMapper.selectList(new LambdaQueryWrapper<ChargePlan>()
                .eq(ChargePlan::getUserId, userId)
                .orderByDesc(ChargePlan::getCreateTime));
        if (plans.isEmpty()) {
            return List.of();
        }
        Set<Long> stationIds = plans.stream().map(ChargePlan::getStationId).collect(Collectors.toSet());
        Map<Long, String> names = stationMapper.selectBatchIds(stationIds).stream()
                .collect(Collectors.toMap(Station::getId, Station::getName));
        return plans.stream().map(p -> toVO(p, names.get(p.getStationId()), null)).collect(Collectors.toList());
    }

    @Override
    public void update(Long planId, Integer enabled, LocalTime chargeTime) {
        ChargePlan plan = ownPlan(planId);
        if (enabled != null) {
            plan.setEnabled(enabled);
        }
        if (chargeTime != null) {
            plan.setChargeTime(chargeTime);
        }
        chargePlanMapper.updateById(plan);
    }

    @Override
    public void delete(Long planId) {
        ChargePlan plan = ownPlan(planId);
        chargePlanMapper.deleteById(plan.getId());
    }

    /** 定时扫描:到点且未触发的计划自动预约,无空闲桩则漏充提醒 */
    @Override
    public void fireDuePlans() {
        LocalDateTime now = LocalDateTime.now();
        int todayDow = now.getDayOfWeek().getValue();
        LocalTime nowTime = now.toLocalTime();

        List<ChargePlan> due = chargePlanMapper.selectList(new LambdaQueryWrapper<ChargePlan>()
                .eq(ChargePlan::getEnabled, 1));
        for (ChargePlan plan : due) {
            if (!containsDow(plan.getCronDays(), todayDow)) {
                continue;
            }
            if (plan.getChargeTime() == null
                    || plan.getChargeTime().isAfter(nowTime)
                    || plan.getChargeTime().plusHours(FIRE_WINDOW_HOURS).isBefore(nowTime)) {
                continue;
            }
            if (plan.getLastFireTime() != null
                    && plan.getLastFireTime().toLocalDate().equals(LocalDate.now())) {
                continue;
            }
            fireOne(plan);
        }
    }

    private void fireOne(ChargePlan plan) {
        try {
            ChargingPile pile = firstIdlePile(plan.getStationId());
            if (pile == null) {
                eventPublisher.publishEvent(ChargingEventBroadcaster.planRemind(plan.getUserId(),
                        "您的固定充电站暂无空闲桩,请尽快前往充电"));
                markFired(plan);
                return;
            }
            reservationService.createQueuedReservation(plan.getUserId(), pile.getId());
            eventPublisher.publishEvent(ChargingEventBroadcaster.planRemind(plan.getUserId(),
                    "已为您自动预约固定站点的 " + pile.getPileNo() + " 号桩"));
            markFired(plan);
        } catch (Exception e) {
            log.warn("自动预约失败 planId={}: {}", plan.getId(), e.getMessage());
            eventPublisher.publishEvent(ChargingEventBroadcaster.planRemind(plan.getUserId(),
                    "自动预约失败:" + e.getMessage() + ",请手动前往充电"));
            markFired(plan);
        }
    }

    private void markFired(ChargePlan plan) {
        plan.setLastFireTime(LocalDateTime.now());
        chargePlanMapper.updateById(plan);
    }

    private ChargingPile firstIdlePile(Long stationId) {
        // 优先快充
        List<ChargingPile> idle = chargingPileMapper.selectList(new LambdaQueryWrapper<ChargingPile>()
                .eq(ChargingPile::getStationId, stationId)
                .eq(ChargingPile::getStatus, "IDLE"));
        return idle.stream()
                .min(Comparator.comparing(p -> "FAST".equals(p.getType()) ? 0 : 1))
                .orElse(null);
    }

    private Station nearestOpenStation(BigDecimal lng, BigDecimal lat) {
        List<Station> stations = stationMapper.selectList(
                new LambdaQueryWrapper<Station>().eq(Station::getStatus, 1));
        if (stations.isEmpty()) {
            return null;
        }
        if (lng == null || lat == null) {
            return stations.get(0);
        }
        return stations.stream()
                .min(Comparator.comparingDouble(s -> mapDistanceProvider.distanceKm(
                        lng.doubleValue(), lat.doubleValue(),
                        s.getLongitude().doubleValue(), s.getLatitude().doubleValue())))
                .orElse(stations.get(0));
    }

    private boolean containsDow(String cronDays, int dow) {
        if (cronDays == null || cronDays.isBlank()) {
            return false;
        }
        return Arrays.stream(cronDays.split(","))
                .map(String::trim)
                .anyMatch(d -> d.equals(String.valueOf(dow)));
    }

    private ChargePlan ownPlan(Long planId) {
        ChargePlan plan = chargePlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (!"ADMIN".equals(UserContext.getRole()) && !plan.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return plan;
    }

    private ChargePlanVO toVO(ChargePlan plan, String stationName, String reason) {
        ChargePlanVO v = new ChargePlanVO();
        v.setId(plan.getId());
        v.setUserId(plan.getUserId());
        v.setStationId(plan.getStationId());
        v.setStationName(stationName);
        v.setCronDays(plan.getCronDays());
        v.setChargeTime(plan.getChargeTime());
        v.setTargetEnergy(plan.getTargetEnergy());
        v.setCommuteKm(plan.getCommuteKm());
        v.setHasHomeCharger(plan.getHasHomeCharger());
        v.setEnabled(plan.getEnabled());
        v.setReason(reason);
        return v;
    }
}
