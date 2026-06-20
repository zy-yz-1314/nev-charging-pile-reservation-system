package com.powerqueue.service;

import com.powerqueue.dto.LoginDTO;
import com.powerqueue.dto.RegisterDTO;
import com.powerqueue.vo.LoginVO;

/**
 * 认证服务。
 */
public interface AuthService {

    /** 注册(默认角色 USER) */
    void register(RegisterDTO dto);

    /** 登录,返回 token 与用户信息 */
    LoginVO login(LoginDTO dto);
}
