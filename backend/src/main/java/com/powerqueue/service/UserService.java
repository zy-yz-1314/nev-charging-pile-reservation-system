package com.powerqueue.service;

import com.powerqueue.dto.UpdateProfileDTO;
import com.powerqueue.vo.UserVO;

import java.math.BigDecimal;

/**
 * 车主用户服务(操作当前登录用户自身)。
 */
public interface UserService {

    UserVO getCurrentUser();

    void updateProfile(UpdateProfileDTO dto);

    void recharge(BigDecimal amount);
}
