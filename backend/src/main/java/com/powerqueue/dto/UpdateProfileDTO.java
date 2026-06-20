package com.powerqueue.dto;

import lombok.Data;

/**
 * 更新个人资料请求。
 */
@Data
public class UpdateProfileDTO {
    private String nickname;
    private String phone;
    private String carPlate;
    private String avatar;
}
