package com.powerqueue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerqueue.common.BusinessException;
import com.powerqueue.common.ResultCode;
import com.powerqueue.entity.ChargingPile;
import com.powerqueue.entity.Station;
import com.powerqueue.mapper.ChargingPileMapper;
import com.powerqueue.mapper.StationMapper;
import com.powerqueue.service.StationService;
import com.powerqueue.vo.StationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationServiceImpl implements StationService {

    private final StationMapper stationMapper;
    private final ChargingPileMapper chargingPileMapper;

    @Override
    public List<StationVO> listStations(String keyword) {
        LambdaQueryWrapper<Station> qw = new LambdaQueryWrapper<>();
        qw.eq(Station::getStatus, 1);
        if (StringUtils.hasText(keyword)) {
            // status=1 AND (name like ? OR address like ?)
            qw.and(w -> w.like(Station::getName, keyword).or().like(Station::getAddress, keyword));
        }
        qw.orderByAsc(Station::getId);
        return stationMapper.selectList(qw).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public StationVO getDetail(Long id) {
        Station s = stationMapper.selectById(id);
        if (s == null) {
            throw new BusinessException(ResultCode.STATION_NOT_FOUND);
        }
        return toVO(s);
    }

    private StationVO toVO(Station s) {
        StationVO v = new StationVO();
        BeanUtils.copyProperties(s, v);
        List<ChargingPile> piles = chargingPileMapper.selectList(
                new LambdaQueryWrapper<ChargingPile>().eq(ChargingPile::getStationId, s.getId()));
        int idle = 0, fastIdle = 0;
        for (ChargingPile p : piles) {
            if ("IDLE".equals(p.getStatus())) {
                idle++;
                if ("FAST".equals(p.getType())) {
                    fastIdle++;
                }
            }
        }
        v.setTotalPiles(piles.size());
        v.setIdlePiles(idle);
        v.setFastIdle(fastIdle);
        return v;
    }
}
