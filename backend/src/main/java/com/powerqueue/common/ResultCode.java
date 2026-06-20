package com.powerqueue.common;

import lombok.Getter;

/**
 * 业务状态码枚举。
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    ERROR(500, "操作失败"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无访问权限"),
    NOT_FOUND(404, "资源不存在"),

    // ===== 用户/认证 =====
    USERNAME_EXISTS(1001, "用户名已存在"),
    LOGIN_ERROR(1002, "用户名或密码错误"),
    USER_DISABLED(1003, "账号已被禁用"),
    USER_NOT_FOUND(1004, "用户不存在"),

    // ===== 充电桩/预约 =====
    PILE_NOT_FOUND(2001, "充电桩不存在"),
    PILE_NOT_IDLE(2002, "手慢了,该充电桩已被抢占"),
    STATION_NOT_FOUND(2003, "充电站不存在"),
    RESERVATION_NOT_FOUND(2004, "预约记录不存在"),
    DUPLICATE_RESERVATION(2005, "您有正在进行的预约,请勿重复抢桩"),
    RESERVATION_STATUS_ERROR(2006, "当前订单状态不允许该操作"),
    GRAB_BUSY(2007, "当前抢桩人数过多,请稍后再试");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
