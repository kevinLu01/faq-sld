package com.sld.faq.module.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.sld.faq.common.ApiResponse;
import com.sld.faq.config.properties.WeComProperties;
import com.sld.faq.module.auth.dto.LoginResponse;
import com.sld.faq.module.auth.dto.MockLoginRequest;
import com.sld.faq.module.auth.dto.WeComCallbackRequest;
import com.sld.faq.module.user.UserService;
import com.sld.faq.module.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证模块 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final WeComProperties weComProperties;

    /**
     * 前端登录配置（无需鉴权）
     * 告知前端是否启用 mock 登录，用于决定显示 mock 按钮还是跳转 OAuth
     */
    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        return ApiResponse.ok(Map.of("mockLoginEnabled", weComProperties.isMockLogin()));
    }

    /**
     * 获取企业微信 OAuth 跳转地址
     *
     * @return OAuth 授权页面 URL
     */
    @GetMapping("/wecom/url")
    public ApiResponse<String> getWeComUrl() {
        return ApiResponse.ok(authService.getWeComOAuthUrl());
    }

    /**
     * 处理企业微信 OAuth 回调，返回 token 和用户信息
     *
     * @param req 回调请求（code + state）
     * @return 登录响应
     */
    @PostMapping("/wecom/callback")
    public ApiResponse<LoginResponse> callback(@RequestBody @Validated WeComCallbackRequest req) {
        return ApiResponse.ok(authService.wecomCallback(req.getCode(), req.getState()));
    }

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

    /**
     * Mock 登录（仅 wecom.mock-login=true 时可用，服务层会二次校验）
     *
     * @param req Mock 登录请求
     * @return 登录响应
     */
    @PostMapping("/mock-login")
    public ApiResponse<LoginResponse> mockLogin(@RequestBody MockLoginRequest req) {
        return ApiResponse.ok(authService.mockLogin(req.getUserId(), req.getName(), req.getRole()));
    }

    /**
     * 登出，清除 Sa-Token 会话
     *
     * @return 空响应
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        StpUtil.logout();
        return ApiResponse.ok();
    }
}
