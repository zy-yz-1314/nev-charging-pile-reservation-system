package com.powerqueue.vo;

import com.powerqueue.entity.User;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 用户信息视图(不含密码)。
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String carPlate;
    private String role;
    private BigDecimal balance;
    private String avatar;

    public static UserVO from(User u) {
        UserVO v = new UserVO();
        v.setId(u.getId());
        v.setUsername(u.getUsername());
        v.setNickname(u.getNickname());
        v.setPhone(u.getPhone());
        v.setCarPlate(u.getCarPlate());
        v.setRole(u.getRole());
        v.setBalance(u.getBalance());
        v.setAvatar(u.getAvatar());
        return v;
    }
}
