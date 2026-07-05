package com.powerqueue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerqueue.dto.RecommendDTO;
import com.powerqueue.entity.ChargingPile;
import com.powerqueue.entity.ScoreWeightConfig;
import com.powerqueue.entity.Station;
import com.powerqueue.mapper.ChargingPileMapper;
import com.powerqueue.mapper.ScoreWeightConfigMapper;
import com.powerqueue.mapper.StationMapper;
import com.powerqueue.service.MapDistanceProvider;
import com.powerqueue.service.PricingService;
import com.powerqueue.service.QueueService;
import com.powerqueue.service.RecommendService;
import com.powerqueue.vo.PileScoreVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 智能匹配推荐引擎实现(L1)。
 *
 * <p>四因子加权打分,各因子先在候选集内做 min-max 归一化到 [0,1]
 * (距离/等待/价格「越小越好」取反),再按权重 w1~w4 求和。权重后台可配(走 Redis 缓存)。
 *
 * <p>功率匹配度定义:min(车支持功率, 桩额定功率) / 桩额定功率 —— 车不支持高功率时,
 * 派到超充桩匹配度低,引导用户去合适功率的桩。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendServiceImpl implements RecommendService {

    private final ChargingPileMapper chargingPileMapper;
    private final StationMapper stationMapper;
    private final ScoreWeightConfigMapper scoreWeightConfigMapper;
    private final MapDistanceProvider mapDistanceProvider;
    private final PricingService pricingService;
    private final QueueService queueService;

    @Value("${powerqueue.recommend.top-n:5}")
    private int defaultTopN;

    @Override
    public List<PileScoreVO> recommend(RecommendDTO dto) {
        int topN = (dto.getTopN() != null && dto.getTopN() > 0) ? dto.getTopN() : defaultTopN;
        ScoreWeightConfig w = loadWeights(dto.getProfile());

        List<ChargingPile> candidates = loadCandidates();
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<Long, Station> stationMap = loadStations(candidates);

        // 1. 计算每桩原始指标
        List<Scored> scored = new ArrayList<>(candidates.size());
        double carPower = dto.getCarPowerKW().doubleValue();
        for (ChargingPile p : candidates) {
            Station s = stationMap.get(p.getStationId());
            if (s == null || s.getLongitude() == null || s.getLatitude() == null) {
                continue;
            }
            double dist = mapDistanceProvider.distanceKm(
                    dto.getLng().doubleValue(), dto.getLat().doubleValue(),
                    s.getLongitude().doubleValue(), s.getLatitude().doubleValue());
            int wait = queueService.estimateWaitMinutes(p.getId());
            BigDecimal finalPrice = pricingService.finalUnitPrice(p.getId());
            double powerMatch = powerMatch(carPower, p.getPower());

            scored.add(new Scored(p, s.getName(), dist, wait, finalPrice.doubleValue(), powerMatch));
        }
        if (scored.isEmpty()) {
            return List.of();
        }

        // 2. 归一化(越小越好的项取反) + 加权
        normalize(scored);
        double w1 = w.getWDistance().doubleValue();
        double w2 = w.getWWait().doubleValue();
        double w3 = w.getWPrice().doubleValue();
        double w4 = w.getWPower().doubleValue();
        for (Scored s : scored) {
            s.score = (w1 * s.distNorm + w2 * s.waitNorm + w3 * s.priceNorm + w4 * s.powerMatch) * 100.0;
        }

        // 3. 排序取 Top-N
        scored.sort(Comparator.comparingDouble((Scored s) -> s.score).reversed());
        return scored.stream().limit(topN).map(this::toVO).collect(Collectors.toList());
    }

    /** 候选桩:非故障、所属站点营业 */
    private List<ChargingPile> loadCandidates() {
        List<ChargingPile> piles = chargingPileMapper.selectList(
                new LambdaQueryWrapper<ChargingPile>().ne(ChargingPile::getStatus, "FAULT"));
        Set<Long> openStationIds = stationMapper.selectList(
                        new LambdaQueryWrapper<Station>().eq(Station::getStatus, 1))
                .stream().map(Station::getId).collect(Collectors.toSet());
        return piles.stream()
                .filter(p -> openStationIds.contains(p.getStationId()))
                .collect(Collectors.toList());
    }

    private Map<Long, Station> loadStations(List<ChargingPile> piles) {
        Set<Long> ids = piles.stream().map(ChargingPile::getStationId).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of()
                : stationMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Station::getId, s -> s));
    }

    /** min-max 归一化;距离/等待/价格越小越好 → 取反。全相等时给 1.0(中性最优)。 */
    private void normalize(List<Scored> list) {
        list.forEach(s -> s.powerNorm = s.powerMatch); // 功率匹配度本身 [0,1]
        invertMinMax(list, x -> x.dist, (x, v) -> x.distNorm = v);
        invertMinMax(list, x -> x.wait, (x, v) -> x.waitNorm = v);
        invertMinMax(list, x -> x.price, (x, v) -> x.priceNorm = v);
    }

    private void invertMinMax(List<Scored> list,
                              java.util.function.ToDoubleFunction<Scored> getter,
                              java.util.function.ObjDoubleConsumer<Scored> setter) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Scored s : list) {
            double v = getter.applyAsDouble(s);
            if (v < min) min = v;
            if (v > max) max = v;
        }
        double range = max - min;
        for (Scored s : list) {
            double v = getter.applyAsDouble(s);
            setter.accept(s, range == 0 ? 1.0 : (max - v) / range);
        }
    }

    private double powerMatch(double carPowerKW, BigDecimal pilePower) {
        double pp = pilePower == null ? 0 : pilePower.doubleValue();
        if (pp <= 0) {
            return 0;
        }
        return Math.min(carPowerKW, pp) / pp;
    }

    /** 打分权重内存缓存 + TTL:避免 GenericJackson2JsonRedisSerializer 对 BigDecimal 字段反序列化异常。 */
    private volatile ScoreWeightConfig cachedDefault;
    private volatile ScoreWeightConfig cachedUrgent;
    private volatile ScoreWeightConfig cachedEconomy;
    private volatile long weightExpireAt;

    private ScoreWeightConfig loadWeights(String profile) {
        String p = (profile == null || profile.isBlank()) ? "default" : profile;
        long now = System.currentTimeMillis();
        if (now >= weightExpireAt) {
            synchronized (this) {
                if (now >= weightExpireAt) {
                    cachedDefault = null; cachedUrgent = null; cachedEconomy = null;
                    weightExpireAt = now + TimeUnit.MINUTES.toMillis(5);
                }
            }
        }
        return switch (p) {
            case "urgent" -> {
                if (cachedUrgent == null) cachedUrgent = loadOrDefault("urgent");
                yield cachedUrgent;
            }
            case "economy" -> {
                if (cachedEconomy == null) cachedEconomy = loadOrDefault("economy");
                yield cachedEconomy;
            }
            default -> {
                if (cachedDefault == null) cachedDefault = loadOrDefault("default");
                yield cachedDefault;
            }
        };
    }

    private ScoreWeightConfig loadOrDefault(String p) {
        ScoreWeightConfig cfg = scoreWeightConfigMapper.selectOne(
                new LambdaQueryWrapper<ScoreWeightConfig>()
                        .eq(ScoreWeightConfig::getProfile, p)
                        .eq(ScoreWeightConfig::getEnabled, 1));
        if (cfg == null && !"default".equals(p)) {
            return loadOrDefault("default");
        }
        return cfg != null ? cfg : defaultWeights();
    }

    private ScoreWeightConfig defaultWeights() {
        ScoreWeightConfig c = new ScoreWeightConfig();
        c.setProfile("default");
        c.setWDistance(new BigDecimal("0.40"));
        c.setWWait(new BigDecimal("0.25"));
        c.setWPrice(new BigDecimal("0.15"));
        c.setWPower(new BigDecimal("0.20"));
        return c;
    }

    private PileScoreVO toVO(Scored s) {
        PileScoreVO v = new PileScoreVO();
        v.setPileId(s.pile.getId());
        v.setStationId(s.pile.getStationId());
        v.setStationName(s.stationName);
        v.setPileNo(s.pile.getPileNo());
        v.setType(s.pile.getType());
        v.setPower(s.pile.getPower());
        v.setBasePrice(s.pile.getPrice());
        v.setFinalPrice(BigDecimal.valueOf(s.price).setScale(2, RoundingMode.HALF_UP));
        v.setDistanceKm(BigDecimal.valueOf(s.dist).setScale(2, RoundingMode.HALF_UP));
        v.setWaitMin(s.wait);
        v.setPowerMatch(BigDecimal.valueOf(s.powerMatch).setScale(2, RoundingMode.HALF_UP));
        v.setScore(BigDecimal.valueOf(s.score).setScale(4, RoundingMode.HALF_UP));
        v.setDistanceScore(BigDecimal.valueOf(s.distNorm).setScale(4, RoundingMode.HALF_UP));
        v.setWaitScore(BigDecimal.valueOf(s.waitNorm).setScale(4, RoundingMode.HALF_UP));
        v.setPriceScore(BigDecimal.valueOf(s.priceNorm).setScale(4, RoundingMode.HALF_UP));
        v.setPowerScore(BigDecimal.valueOf(s.powerMatch).setScale(4, RoundingMode.HALF_UP));
        return v;
    }

    /** 打分中间结构。 */
    private static final class Scored {
        final ChargingPile pile;
        final String stationName;
        final double dist;
        final int wait;
        final double price;
        final double powerMatch;
        double distNorm;
        double waitNorm;
        double priceNorm;
        double powerNorm;
        double score;

        Scored(ChargingPile pile, String stationName, double dist, int wait, double price, double powerMatch) {
            this.pile = pile;
            this.stationName = stationName;
            this.dist = dist;
            this.wait = wait;
            this.price = price;
            this.powerMatch = powerMatch;
        }
    }
}
