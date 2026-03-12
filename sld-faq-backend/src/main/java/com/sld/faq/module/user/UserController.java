package com.sld.faq.module.user;

import cn.dev33.satoken.stp.StpUtil;
import com.sld.faq.common.ApiResponse;
import com.sld.faq.module.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户模块 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 获取当前登录用户信息
     *
     * @return 当前用户视图对象
     */
    @GetMapping("/me")
    public ApiResponse<UserVO> me() {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.ok(userService.getUserVO(userId));
    }
}
