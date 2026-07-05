package com.powerqueue.common.enums;

import lombok.Getter;

/**
 * 充电桩状态。
 * 枚举名(name)即数据库存储值。
 */
@Getter
public enum PileStatus {

    IDLE("空闲"),
    RESERVED("已预约"),
    CHARGING("充电中"),
    FAULT("故障"),
    QUEUED("排队中");

    private final String desc;

    PileStatus(String desc) {
        this.desc = desc;
    }
}
