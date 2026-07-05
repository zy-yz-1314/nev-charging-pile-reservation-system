package com.powerqueue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerqueue.common.BusinessException;
import com.powerqueue.common.ResultCode;
import com.powerqueue.entity.ChargingPile;
import com.powerqueue.entity.PricingRule;
import com.powerqueue.mapper.ChargingPileMapper;
import com.powerqueue.mapper.PricingRuleMapper;
import com.powerqueue.service.PricingService;
import com.powerqueue.vo.PriceCalcVO;
import com.powerqueue.vo.StationLoadVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

/**
 * 动态供需定价实现(L2)。
 *
 * <p>最终电价 = 基础电价 × 时段系数 × 站点负载系数
 * <ul>
 *   <li>时段系数:谷时 0-8 点 0.7、平段 1.0、峰时 17-21 点 1.5;</li>
 *   <li>负载系数:空闲率>50% 0.9(引流)、<20% 1.3(分流),中间 1.0;</li>
 *   <li>阈值边界加滞后区(hysteresis)防价格震荡。</li>
 * </ul>
 * 系数优先取 pricing_rule 表(后台可调,Redis 缓存 5min),表空回退 application.yml 默认值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {

    private static final String BAND_PREFIX = "station:load:band:";
    private static final String CONFIG_CACHE_KEY = "config:pricing";

    private final ChargingPileMapper chargingPileMapper;
    private final PricingRuleMapper pricingRuleMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public BigDecimal finalUnitPrice(Long pileId) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) {
            throw new BusinessException(ResultCode.PILE_NOT_FOUND);
        }
        Coeff c = loadCoefficients();
        BigDecimal time = BigDecimal.valueOf(timeCoefficient(LocalDateTime.now(), c));
        BigDecimal load = stationLoad(pile.getStationId()).getLoadCoefficient();
        return pile.getPrice().multiply(time).multiply(load).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public PriceCalcVO calc(Long pileId, LocalDateTime at) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) {
            throw new BusinessException(ResultCode.PILE_NOT_FOUND);
        }
        LocalDateTime moment = at != null ? at : LocalDateTime.now();
        Coeff c = loadCoefficients();
        StationLoadVO load = stationLoad(pile.getStationId());

        PriceCalcVO v = new PriceCalcVO();
        v.setPileId(pileId);
        v.setStationId(pile.getStationId());
        v.setBasePrice(pile.getPrice());
        v.setTimeCoefficient(BigDecimal.valueOf(timeCoefficient(moment, c)).setScale(2, RoundingMode.HALF_UP));
        v.setLoadCoefficient(load.getLoadCoefficient());
        v.setFinalPrice(pile.getPrice()
                .multiply(v.getTimeCoefficient())
                .multiply(v.getLoadCoefficient())
                .setScale(2, RoundingMode.HALF_UP));
        v.setSegment(segmentOf(moment.toLocalTime(), c));
        v.setIdleRate(load.getIdleRate());
        return v;
    }

    @Override
    public StationLoadVO stationLoad(Long stationId) {
        Long total = chargingPileMapper.selectCount(new LambdaQueryWrapper<ChargingPile>()
                .eq(ChargingPile::getStationId, stationId)
                .ne(ChargingPile::getStatus, "FAULT"));
        Long idle = chargingPileMapper.selectCount(new LambdaQueryWrapper<ChargingPile>()
                .eq(ChargingPile::getStationId, stationId)
                .eq(ChargingPile::getStatus, "IDLE"));
        long t = total == null ? 0 : total;
        long i = idle == null ? 0 : idle;
        BigDecimal idleRate = t == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(i).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(t), 2, RoundingMode.HALF_UP);

        Coeff c = loadCoefficients();
        String band = bandWithHysteresis(stationId, idleRate.doubleValue(), c);

        StationLoadVO v = new StationLoadVO();
        v.setStationId(stationId);
        v.setIdle((int) i);
        v.setTotal((int) t);
        v.setIdleRate(idleRate);
        v.setLoadLevel(levelOf(band));
        v.setLoadCoefficient(coefficientOf(band, c));
        return v;
    }

    // ============ 时段系数 ============

    /** 谷时 0-8 点 0.7;峰时 17-21 点 1.5;其余平段 1.0 */
    private double timeCoefficient(LocalDateTime at, Coeff c) {
        LocalTime t = at.toLocalTime();
        if (!t.isBefore(c.valleyStart) && t.isBefore(c.valleyEnd)) {
            return c.valley;
        }
        if (!t.isBefore(c.peakStart) && t.isBefore(c.peakEnd)) {
            return c.peak;
        }
        return c.flat;
    }

    private String segmentOf(LocalTime t, Coeff c) {
        if (!t.isBefore(c.valleyStart) && t.isBefore(c.valleyEnd)) {
            return "VALLEY";
        }
        if (!t.isBefore(c.peakStart) && t.isBefore(c.peakEnd)) {
            return "PEAK";
        }
        return "FLAT";
    }

    // ============ 负载系数 + 滞后区 ============

    /**
     * 带滞后区的负载档位:HIGH(空闲率>50%)/ LOW(<20%)/ MID。
     * 滞后区避免占用率在阈值附近抖动导致系数反复跳:
     * <ul>
     *   <li>当前 HIGH:降到 ≤(50-hys) 才退到 MID;</li>
     *   <li>当前 LOW:升到 ≥(20+hys) 才升到 MID;</li>
     *   <li>当前 MID:升过 50+hys 进 HIGH,降过 20-hys 进 LOW。</li>
     * </ul>
     */
    private String bandWithHysteresis(Long stationId, double idleRate, Coeff c) {
        String key = BAND_PREFIX + stationId;
        Object cached = redisTemplate.opsForValue().get(key);
        String cur = cached == null ? null : cached.toString();
        double hys = c.hysteresis;
        String next;
        if (cur == null) {
            next = rawBand(idleRate);
        } else if ("HIGH".equals(cur)) {
            next = idleRate <= (50 - hys) ? "MID" : "HIGH";
        } else if ("LOW".equals(cur)) {
            next = idleRate >= (20 + hys) ? "MID" : "LOW";
        } else {
            if (idleRate > 50 + hys) {
                next = "HIGH";
            } else if (idleRate < 20 - hys) {
                next = "LOW";
            } else {
                next = "MID";
            }
        }
        if (!next.equals(cur)) {
            redisTemplate.opsForValue().set(key, next);
        }
        return next;
    }

    private String rawBand(double idleRate) {
        if (idleRate > 50) {
            return "HIGH";
        }
        if (idleRate < 20) {
            return "LOW";
        }
        return "MID";
    }

    private String levelOf(String band) {
        return switch (band) {
            case "HIGH" -> "GREEN";
            case "LOW" -> "RED";
            default -> "YELLOW";
        };
    }

    private BigDecimal coefficientOf(String band, Coeff c) {
        BigDecimal raw = switch (band) {
            case "HIGH" -> c.highIdle;   // 0.90 引流
            case "LOW" -> c.lowIdle;     // 1.30 分流
            default -> c.midIdle;        // 1.00
        };
        return raw.setScale(2, RoundingMode.HALF_UP);
    }

    // ============ 系数加载(DB 优先 + 内存缓存 TTL,回退默认) ============

    /** 内存缓存:只存简单对象,不经过 Redis 类型序列化,避免 GenericJackson2JsonRedisSerializer
     *  反序列化私有内部类 + BigDecimal 时抛出 InvalidTypeIdException。 */
    private volatile Coeff cachedCoeff;
    private volatile long coeffExpireAt;

    private Coeff loadCoefficients() {
        long now = System.currentTimeMillis();
        if (cachedCoeff != null && now < coeffExpireAt) {
            return cachedCoeff;
        }
        synchronized (this) {
            if (cachedCoeff != null && now < coeffExpireAt) {
                return cachedCoeff;
            }
            Coeff c = loadFromDb();
            if (c == null) {
                c = Coeff.defaults();
            }
            cachedCoeff = c;
            coeffExpireAt = now + TimeUnit.MINUTES.toMillis(5);
            return c;
        }
    }

    private Coeff loadFromDb() {
        var rules = pricingRuleMapper.selectList(new LambdaQueryWrapper<PricingRule>()
                .eq(PricingRule::getEnabled, 1));
        if (rules.isEmpty()) {
            return null;
        }
        Coeff c = Coeff.defaults();
        for (PricingRule r : rules) {
            if ("TIME".equals(r.getRuleType())) {
                switch (r.getSegmentKey()) {
                    case "VALLEY" -> { c.valley = val(r); c.valleyStart = r.getTimeStart(); c.valleyEnd = r.getTimeEnd(); }
                    case "PEAK" -> { c.peak = val(r); c.peakStart = r.getTimeStart(); c.peakEnd = r.getTimeEnd(); }
                    case "FLAT" -> c.flat = val(r);
                }
            } else {
                switch (r.getSegmentKey()) {
                    case "HIGH" -> c.highIdle = BigDecimal.valueOf(val(r));
                    case "LOW" -> c.lowIdle = BigDecimal.valueOf(val(r));
                    case "MID" -> c.midIdle = BigDecimal.valueOf(val(r));
                }
            }
        }
        return c;
    }

    private double val(PricingRule r) {
        return r.getCoefficient() == null ? 1.0d : r.getCoefficient().doubleValue();
    }

    /** 系数集合(含时段边界与滞后量)。 */
    private static final class Coeff {
        double valley = 0.70, flat = 1.00, peak = 1.50;
        BigDecimal highIdle = new BigDecimal("0.90");
        BigDecimal midIdle = new BigDecimal("1.00");
        BigDecimal lowIdle = new BigDecimal("1.30");
        LocalTime valleyStart = LocalTime.MIDNIGHT;
        LocalTime valleyEnd = LocalTime.of(8, 0);
        LocalTime peakStart = LocalTime.of(17, 0);
        LocalTime peakEnd = LocalTime.of(21, 0);
        double hysteresis = 5.0;

        static Coeff defaults() {
            return new Coeff();
        }
    }
}
