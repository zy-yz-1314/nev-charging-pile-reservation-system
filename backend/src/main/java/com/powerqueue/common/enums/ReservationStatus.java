package com.powerqueue.common.enums;

import lombok.Getter;

/**
 * 预约/充电订单状态。
 * 枚举名(name)即数据库存储值。
 */
@Getter
public enum ReservationStatus {

    PENDING("待充电"),
    QUEUED("排队中"),
    WAITING_CONFIRM("待确认占位"),
    CHARGING("充电中"),
    FINISHED("已完成"),
    CANCELLED("已取消");

    private final String desc;

    ReservationStatus(String desc) {
        this.desc = desc;
    }
}
