package com.powerqueue.service.admin.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.powerqueue.entity.ChargingPile;
import com.powerqueue.mapper.ChargingPileMapper;
import com.powerqueue.service.admin.AdminPileService;
import org.springframework.stereotype.Service;

@Service
public class AdminPileServiceImpl extends ServiceImpl<ChargingPileMapper, ChargingPile>
        implements AdminPileService {
}
