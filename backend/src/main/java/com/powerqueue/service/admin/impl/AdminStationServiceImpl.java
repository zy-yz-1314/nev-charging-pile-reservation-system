package com.powerqueue.service.admin.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.powerqueue.entity.Station;
import com.powerqueue.mapper.StationMapper;
import com.powerqueue.service.admin.AdminStationService;
import org.springframework.stereotype.Service;

@Service
public class AdminStationServiceImpl extends ServiceImpl<StationMapper, Station>
        implements AdminStationService {
}
