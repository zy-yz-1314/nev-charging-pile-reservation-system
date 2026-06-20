package com.powerqueue.service.admin.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerqueue.mapper.ReservationMapper;
import com.powerqueue.service.admin.AdminReservationService;
import com.powerqueue.vo.ReservationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminReservationServiceImpl implements AdminReservationService {

    private final ReservationMapper reservationMapper;

    @Override
    public IPage<ReservationVO> page(long current, long size, String status, String keyword) {
        IPage<ReservationVO> page = reservationMapper.pageReservationVO(
                new Page<>(current, size), status, keyword);
        page.getRecords().forEach(r -> r.setStatusDesc(statusDesc(r.getStatus())));
        return page;
    }

    private String statusDesc(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "PENDING" -> "待充电";
            case "CHARGING" -> "充电中";
            case "FINISHED" -> "已完成";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }
}
