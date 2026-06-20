package com.powerqueue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerqueue.common.BusinessException;
import com.powerqueue.common.ResultCode;
import com.powerqueue.entity.ChargingPile;
import com.powerqueue.mapper.ChargingPileMapper;
import com.powerqueue.service.ChargingPileService;
import com.powerqueue.vo.PileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 充电桩服务实现。
 * <p>
 * 缓存策略(缓存旁路 Cache-Aside):
 * 读取时先查 Redis,命中直接返回;未命中查 MySQL 后回填缓存并设置 TTL。
 * 抢桩/状态变更后主动失效缓存,保证下一次读取拿到最新状态。
 */
@Service
@RequiredArgsConstructor
public class ChargingPileServiceImpl implements ChargingPileService {

    private static final String CACHE_PREFIX = "station:piles:";

    private final ChargingPileMapper chargingPileMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${powerqueue.cache.pile-ttl-seconds:30}")
    private long ttlSeconds;

    private String cacheKey(Long stationId) {
        return CACHE_PREFIX + stationId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<PileVO> listByStation(Long stationId) {
        String key = cacheKey(stationId);
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return (List<PileVO>) cached;
        }
        List<PileVO> vos = chargingPileMapper.selectList(
                        new LambdaQueryWrapper<ChargingPile>()
                                .eq(ChargingPile::getStationId, stationId)
                                .orderByAsc(ChargingPile::getPileNo))
                .stream()
                .map(PileVO::from)
                .collect(Collectors.toList());
        redisTemplate.opsForValue().set(key, vos, ttlSeconds, TimeUnit.SECONDS);
        return vos;
    }

    @Override
    public void evictStationCache(Long stationId) {
        redisTemplate.delete(cacheKey(stationId));
    }

    @Override
    public ChargingPile getByIdOrThrow(Long pileId) {
        ChargingPile p = chargingPileMapper.selectById(pileId);
        if (p == null) {
            throw new BusinessException(ResultCode.PILE_NOT_FOUND);
        }
        return p;
    }
}
