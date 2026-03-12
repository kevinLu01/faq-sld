package com.sld.faq.config;

import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sld.faq.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 过滤器配置
 * <p>
 * 拦截所有接口，排除公开接口（企业微信 OAuth 相关 + mock 登录），
 * 对其余接口执行 {@link StpUtil#checkLogin()} 校验。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SaTokenConfig {

    private final ObjectMapper objectMapper;

    @Bean
    public SaServletFilter getSaServletFilter() {
        return new SaServletFilter()
                // 拦截所有路径
                .addInclude("/**")
                // 排除公开接口
                .addExclude(
                        "/api/auth/wecom/url",
                        "/api/auth/wecom/callback",
                        "/api/auth/mock-login"
                )
                // 认证逻辑：校验登录状态
                .setAuth(obj -> {
                    SaRouter.match("/**").check(r -> StpUtil.checkLogin());
                })
                // 异常处理：返回标准 JSON 响应
                .setError(e -> {
                    try {
                        ApiResponse<Void> resp = ApiResponse.error(401, "未登录");
                        return objectMapper.writeValueAsString(resp);
                    } catch (Exception ex) {
                        log.error("Sa-Token 异常序列化失败", ex);
                        return "{\"code\":401,\"message\":\"未登录\",\"data\":null}";
                    }
                });
    }
}
