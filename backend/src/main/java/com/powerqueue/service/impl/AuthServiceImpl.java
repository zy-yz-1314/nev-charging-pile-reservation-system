package com.powerqueue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerqueue.common.BusinessException;
import com.powerqueue.common.ResultCode;
import com.powerqueue.dto.LoginDTO;
import com.powerqueue.dto.RegisterDTO;
import com.powerqueue.entity.User;
import com.powerqueue.mapper.UserMapper;
import com.powerqueue.service.AuthService;
import com.powerqueue.utils.JwtUtil;
import com.powerqueue.vo.LoginVO;
import com.powerqueue.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public void register(RegisterDTO dto) {
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }
        User u = new User();
        u.setUsername(dto.getUsername());
        u.setPassword(passwordEncoder.encode(dto.getPassword()));
        u.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        u.setPhone(dto.getPhone());
        u.setCarPlate(dto.getCarPlate());
        u.setRole("USER");
        u.setBalance(new BigDecimal("100.00")); // 注册赠送体验金
        u.setStatus(1);
        userMapper.insert(u);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        User u = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (u == null || !passwordEncoder.matches(dto.getPassword(), u.getPassword())) {
            throw new BusinessException(ResultCode.LOGIN_ERROR);
        }
        if (u.getStatus() != null && u.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        String token = jwtUtil.generate(u.getId(), u.getUsername(), u.getRole());
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUser(UserVO.from(u));
        return vo;
    }
}
