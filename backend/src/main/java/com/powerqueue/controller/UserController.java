package com.powerqueue.controller;

import com.powerqueue.common.Result;
import com.powerqueue.dto.UpdateProfileDTO;
import com.powerqueue.service.UserService;
import com.powerqueue.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 车主个人中心接口(操作自身,需登录)。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 获取当前登录用户信息 */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(userService.getCurrentUser());
    }

    /** 更新个人资料 */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody UpdateProfileDTO dto) {
        userService.updateProfile(dto);
        return Result.success("更新成功", null);
    }

    /** 余额充值 */
    @PostMapping("/recharge")
    public Result<Void> recharge(@RequestParam BigDecimal amount) {
        userService.recharge(amount);
        return Result.success("充值成功", null);
    }
}
