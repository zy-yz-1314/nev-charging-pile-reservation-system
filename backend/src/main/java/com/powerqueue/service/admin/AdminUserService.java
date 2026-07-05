package com.powerqueue.service.admin;

import com.baomidou.mybatisplus.extension.service.IService;
import com.powerqueue.entity.User;

/**
 * 管理后台 - 用户服务。
 */
public interface AdminUserService extends IService<User> {

    /** 新增用户(密码加密,未填则使用系统默认密码) */
    void createUser(User user);

    /** 重置密码为系统默认密码 */
    void resetPassword(Long id);

    /** 启用/禁用 */
    void toggleStatus(Long id, Integer status);
}
