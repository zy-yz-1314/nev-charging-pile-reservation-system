package com.powerqueue.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预约 / 订单视图(含充电桩与站点冗余信息,便于前端展示)。
 */
@Data
public class ReservationVO {

    private Long id;
    private String orderNo;

    private Long pileId;
    private String pileNo;
    private String pileType;
    private BigDecimal power;
    private BigDecimal price;

    private Long stationId;
    private String stationName;

    /** 下单用户(管理后台订单列表展示用) */
    private String userName;

    private LocalDateTime reserveTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer duration;
    private BigDecimal powerUsed;
    private BigDecimal amount;

    private String status;
    private String statusDesc;
}
