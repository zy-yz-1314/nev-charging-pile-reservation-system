package com.powerqueue.service;

import com.powerqueue.entity.ChargingPile;
import com.powerqueue.vo.PileVO;

import java.util.List;

/**
 * 充电桩服务。充电桩实时状态读取走 Redis 缓存以降低 MySQL 读压力。
 */
public interface ChargingPileService {

    /** 查询某充电站下的充电桩实时状态(缓存旁路) */
    List<PileVO> listByStation(Long stationId);

    /** 清除某站点的充电桩缓存(抢桩/状态变更后调用) */
    void evictStationCache(Long stationId);

    /** 按 ID 获取充电桩,不存在则抛异常 */
    ChargingPile getByIdOrThrow(Long pileId);
}
