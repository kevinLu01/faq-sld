package com.sld.faq.module.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.sld.faq.common.BusinessException;
import com.sld.faq.config.properties.WeComProperties;
import com.sld.faq.infrastructure.wecom.WeComOAuthService;
import com.sld.faq.module.auth.dto.LoginResponse;
import com.sld.faq.module.user.UserService;
import com.sld.faq.module.user.entity.SysUser;
import com.sld.faq.module.user.vo.UserVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证业务服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final WeComOAuthService weComOAuthService;
    private final WeComProperties weComProperties;
    private final UserService userService;

    @PostConstruct
    void warnIfMockLoginEnabled() {
        if (weComProperties.isMockLogin()) {
            log.warn("Mock login is enabled! Do NOT use in production. Set WECOM_MOCK_LOGIN=false for production deployments.");
        }
    }

    /**
     * 生成企业微信 OAuth 授权跳转 URL
     *
     * @return 企业微信 OAuth 授权页面完整 URL（含 state）
     */
    public String getWeComOAuthUrl() {
        return weComOAuthService.buildOAuthUrl();
    }

    /**
     * 处理企业微信 OAuth 回调
     * <p>
     * 流程：校验 state → 获取用户信息 → findOrCreate → Sa-Token 登录 → 返回 token
     *
     * @param code  企业微信回调 code
     * @param state 回调 state（防 CSRF）
     * @return 登录响应（token + 用户信息）
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse wecomCallback(String code, String state) {
        // 1. 校验 state 防 CSRF
        weComOAuthService.validateState(state);

        // 2. 用 code 换取企业微信用户信息
        WeComOAuthService.WeComUserInfo userInfo = weComOAuthService.getUserInfo(code);

        // 3. 查找或创建用户（默认角色 SUBMITTER）
        SysUser user = userService.findOrCreate(
                userInfo.getWecomUserId(),
                userInfo.getName(),
                userInfo.getAvatar(),
                userInfo.getMobile()
        );

        // 4. Sa-Token 登录，token 自动存 Redis
        StpUtil.login(user.getId());

        // 5. 获取签发的 token
        String token = StpUtil.getTokenValue();

        // 6. 构建用户视图
        UserVO vo = userService.getUserVO(user.getId());

        log.info("企业微信 OAuth 登录成功: userId={}, name={}", user.getId(), user.getName());
        return new LoginResponse(token, vo);
    }

    /**
     * Mock 登录（仅 wecom.mock-login=true 时可调用）
     * <p>
     * 用于本地开发调试，生产环境应禁用。
     *
     * @param wecomUserId 企业微信用户 ID
     * @param name        用户姓名
     * @param role        角色代码：ADMIN | REVIEWER | SUBMITTER
     * @return 登录响应（token + 用户信息）
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse mockLogin(String wecomUserId, String name, String role) {
        if (!weComProperties.isMockLogin()) {
            throw new BusinessException(403, "mock登录已禁用");
        }

        log.warn("Mock login invoked: wecomUserId={}, role={}. Do NOT use in production.", wecomUserId, role);

        // 使用默认角色 SUBMITTER 创建或查找用户
        SysUser user = userService.findOrCreate(wecomUserId, name, null, null);

        // 若指定角色不是 SUBMITTER，额外绑定指定角色
        if (role != null && !role.isBlank() && !"SUBMITTER".equals(role)) {
            userService.ensureRole(user.getId(), role);
        }

        // Sa-Token 登录
        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();

        UserVO vo = userService.getUserVO(user.getId());

        log.info("Mock 登录成功: wecomUserId={}, role={}", wecomUserId, role);
        return new LoginResponse(token, vo);
    }
}
