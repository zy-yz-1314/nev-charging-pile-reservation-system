package com.powerqueue.service.admin.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.powerqueue.common.BusinessException;
import com.powerqueue.common.ResultCode;
import com.powerqueue.entity.User;
import com.powerqueue.mapper.UserMapper;
import com.powerqueue.service.admin.AdminUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
public class AdminUserServiceImpl extends ServiceImpl<UserMapper, User>
        implements AdminUserService {

    private final PasswordEncoder passwordEncoder;

    @Value("${powerqueue.demo.default-password:powerqueue_demo_2026}")
    private String demoDefaultPassword;

    public AdminUserServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void createUser(User user) {
        String raw = StringUtils.hasText(user.getPassword()) ? user.getPassword() : demoDefaultPassword;
        user.setPassword(passwordEncoder.encode(raw));
        if (!StringUtils.hasText(user.getRole())) {
            user.setRole("USER");
        }
        if (user.getBalance() == null) {
            user.setBalance(BigDecimal.ZERO);
        }
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        save(user);
    }

    @Override
    public void resetPassword(Long id) {
        User u = getById(id);
        if (u == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        u.setPassword(passwordEncoder.encode(demoDefaultPassword));
        updateById(u);
    }

    @Override
    public void toggleStatus(Long id, Integer status) {
        User u = getById(id);
        if (u == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        u.setStatus(status);
        updateById(u);
    }
}
