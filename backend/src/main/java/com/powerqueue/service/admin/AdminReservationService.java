package com.powerqueue.service.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.powerqueue.vo.ReservationVO;

/**
 * 管理后台 - 订单服务。
 */
public interface AdminReservationService {

    IPage<ReservationVO> page(long current, long size, String status, String keyword);
}
