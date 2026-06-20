package com.powerqueue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powerqueue.entity.ChargingPile;
import org.apache.ibatis.annotations.Param;

/**
 * 充电桩 Mapper。
 * 除基础 CRUD 外,提供抢桩的乐观锁更新(自定义 SQL,见 ChargingPileMapper.xml)。
 */
public interface ChargingPileMapper extends BaseMapper<ChargingPile> {

    /**
     * 抢桩核心 SQL(防超卖):仅当桩处于 IDLE 且版本号匹配时才更新成功。
     *
     * @return 影响行数,1 表示抢占成功,0 表示已被他人抢占
     */
    int grabPile(@Param("pileId") Long pileId, @Param("version") Integer version);

    /**
     * 更新充电桩状态(状态流转:RESERVED→CHARGING→IDLE 等)。
     *
     * @return 影响行数
     */
    int updateStatus(@Param("pileId") Long pileId, @Param("status") String status);
}
