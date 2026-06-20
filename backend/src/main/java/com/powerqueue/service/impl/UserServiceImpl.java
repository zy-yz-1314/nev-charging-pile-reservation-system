package com.powerqueue.service.impl;

import com.powerqueue.common.BusinessException;
import com.powerqueue.common.ResultCode;
import com.powerqueue.common.UserContext;
import com.powerqueue.dto.UpdateProfileDTO;
import com.powerqueue.entity.User;
import com.powerqueue.mapper.UserMapper;
import com.powerqueue.service.UserService;
import com.powerqueue.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public UserVO getCurrentUser() {
        User u = userMapper.selectById(UserContext.getUserId());
        if (u == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return UserVO.from(u);
    }

    @Override
    public void updateProfile(UpdateProfileDTO dto) {
        User u = userMapper.selectById(UserContext.getUserId());
        if (u == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (StringUtils.hasText(dto.getNickname())) {
            u.setNickname(dto.getNickname());
        }
        if (dto.getPhone() != null) {
            u.setPhone(dto.getPhone());
        }
        if (dto.getCarPlate() != null) {
            u.setCarPlate(dto.getCarPlate());
        }
        if (dto.getAvatar() != null) {
            u.setAvatar(dto.getAvatar());
        }
        userMapper.updateById(u);
    }

    @Override
    public void recharge(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("充值金额必须大于 0");
        }
        User u = userMapper.selectById(UserContext.getUserId());
        if (u == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        u.setBalance(u.getBalance().add(amount));
        userMapper.updateById(u);
    }
}
