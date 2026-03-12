package com.sld.faq.module.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.sld.faq.common.BusinessException;
import com.sld.faq.config.properties.WeComProperties;
import com.sld.faq.infrastructure.wecom.WeComOAuthService;
import com.sld.faq.module.auth.dto.LoginResponse;
import com.sld.faq.module.user.UserService;
import com.sld.faq.module.user.entity.SysUser;
import com.sld.faq.module.user.vo.UserVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthService 单元测试
 * <p>
 * 覆盖认证业务逻辑：
 * 1. 企业微信 OAuth 回调正常流程
 * 2. state 校验失败时抛出 BusinessException
 * 3. mock 登录开关开启时正常返回
 * 4. mock 登录开关关闭时抛出 BusinessException
 * <p>
 * 注意：StpUtil.login() / getTokenValue() 是静态方法，使用 Mockito.mockStatic() 进行 mock。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 认证服务测试")
class AuthServiceTest {

    @Mock
    private WeComOAuthService weComOAuthService;

    @Mock
    private WeComProperties weComProperties;

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthService authService;

    // ========== wecomCallback 测试 ==========

    @Test
    @DisplayName("正常 code + state 应完成登录并返回 token 和用户信息")
    void wecomCallback_validCodeAndState_returnsLoginResponse() {
        // Arrange
        String code = "oauth_code_12345";
        String state = "csrf_state_abcde";

        WeComOAuthService.WeComUserInfo userInfo = new WeComOAuthService.WeComUserInfo();
        userInfo.setWecomUserId("ZhangSan_WeCom");
        userInfo.setName("张三");
        userInfo.setAvatar("https://cdn.example.com/avatar/zhangsan.jpg");
        userInfo.setMobile("13800138000");

        SysUser sysUser = new SysUser();
        sysUser.setId(101L);
        sysUser.setName("张三");
        sysUser.setWecomUserId("ZhangSan_WeCom");

        UserVO userVO = new UserVO();
        userVO.setId(101L);
        userVO.setName("张三");
        userVO.setRoles(List.of("SUBMITTER"));

        doNothing().when(weComOAuthService).validateState(state);
        when(weComOAuthService.getUserInfo(code)).thenReturn(userInfo);
        when(userService.findOrCreate(
                eq("ZhangSan_WeCom"), eq("张三"),
                eq("https://cdn.example.com/avatar/zhangsan.jpg"), eq("13800138000"))
        ).thenReturn(sysUser);
        when(userService.getUserVO(101L)).thenReturn(userVO);

        try (MockedStatic<StpUtil> stpUtilMock = mockStatic(StpUtil.class)) {
            stpUtilMock.when(() -> StpUtil.login(101L)).thenAnswer(inv -> null);
            stpUtilMock.when(StpUtil::getTokenValue).thenReturn("sa-token-uuid-xyz789");

            // Act
            LoginResponse response = authService.wecomCallback(code, state);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getToken()).isEqualTo("sa-token-uuid-xyz789");
            assertThat(response.getUser()).isNotNull();
            assertThat(response.getUser().getName()).isEqualTo("张三");
            assertThat(response.getUser().getRoles()).contains("SUBMITTER");

            // 验证关键方法都被调用
            verify(weComOAuthService).validateState(state);
            verify(weComOAuthService).getUserInfo(code);
            verify(userService).findOrCreate(anyString(), anyString(), anyString(), anyString());
            stpUtilMock.verify(() -> StpUtil.login(101L));
        }
    }

    @Test
    @DisplayName("state 校验失败时应抛出 BusinessException")
    void wecomCallback_invalidState_throwsBusinessException() {
        // Arrange
        String code = "any_code";
        String invalidState = "forged_state_xyz";

        doThrow(new BusinessException(40010, "OAuth state 无效或已过期，请重新发起登录"))
                .when(weComOAuthService).validateState(invalidState);

        // Act & Assert
        assertThatThrownBy(() -> authService.wecomCallback(code, invalidState))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OAuth state 无效");

        // 确保 state 校验失败后，getUserInfo 不被调用
        verify(weComOAuthService, never()).getUserInfo(anyString());
        verify(userService, never()).findOrCreate(anyString(), anyString(), any(), any());
    }

    // ========== mockLogin 测试 ==========

    @Test
    @DisplayName("mock 开关开启时应正常完成登录并返回 LoginResponse")
    void mockLogin_whenMockEnabled_returnsLoginResponse() {
        // Arrange
        when(weComProperties.isMockLogin()).thenReturn(true);

        SysUser sysUser = new SysUser();
        sysUser.setId(202L);
        sysUser.setName("李四（测试账号）");
        sysUser.setWecomUserId("LiSi_Dev");

        UserVO userVO = new UserVO();
        userVO.setId(202L);
        userVO.setName("李四（测试账号）");
        userVO.setRoles(List.of("REVIEWER"));

        when(userService.findOrCreate(eq("LiSi_Dev"), eq("李四（测试账号）"), isNull(), isNull()))
                .thenReturn(sysUser);
        doNothing().when(userService).ensureRole(202L, "REVIEWER");
        when(userService.getUserVO(202L)).thenReturn(userVO);

        try (MockedStatic<StpUtil> stpUtilMock = mockStatic(StpUtil.class)) {
            stpUtilMock.when(() -> StpUtil.login(202L)).thenAnswer(inv -> null);
            stpUtilMock.when(StpUtil::getTokenValue).thenReturn("mock-sa-token-abc123");

            // Act
            LoginResponse response = authService.mockLogin("LiSi_Dev", "李四（测试账号）", "REVIEWER");

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getToken()).isEqualTo("mock-sa-token-abc123");
            assertThat(response.getUser().getRoles()).contains("REVIEWER");

            verify(userService).ensureRole(202L, "REVIEWER");
            stpUtilMock.verify(() -> StpUtil.login(202L));
        }
    }

    @Test
    @DisplayName("mock 登录开关关闭时应抛出 BusinessException（403）")
    void mockLogin_whenMockDisabled_throwsBusinessException() {
        // Arrange
        when(weComProperties.isMockLogin()).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authService.mockLogin("AnyUser", "任意用户", "ADMIN"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mock登录已禁用")
                .extracting("code")
                .isEqualTo(403);

        // mock 关闭时不应触发任何用户查找逻辑
        verify(userService, never()).findOrCreate(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("mockLogin 角色为 SUBMITTER 时不应调用 ensureRole")
    void mockLogin_submitterRole_doesNotCallEnsureRole() {
        // Arrange
        when(weComProperties.isMockLogin()).thenReturn(true);

        SysUser sysUser = new SysUser();
        sysUser.setId(303L);
        sysUser.setName("王五");
        sysUser.setWecomUserId("WangWu_Dev");

        UserVO userVO = new UserVO();
        userVO.setId(303L);
        userVO.setName("王五");
        userVO.setRoles(List.of("SUBMITTER"));

        when(userService.findOrCreate(eq("WangWu_Dev"), eq("王五"), isNull(), isNull()))
                .thenReturn(sysUser);
        when(userService.getUserVO(303L)).thenReturn(userVO);

        try (MockedStatic<StpUtil> stpUtilMock = mockStatic(StpUtil.class)) {
            stpUtilMock.when(() -> StpUtil.login(303L)).thenAnswer(inv -> null);
            stpUtilMock.when(StpUtil::getTokenValue).thenReturn("mock-submitter-token");

            // Act
            authService.mockLogin("WangWu_Dev", "王五", "SUBMITTER");

            // Assert: SUBMITTER 角色不需要额外的 ensureRole 调用
            verify(userService, never()).ensureRole(anyLong(), anyString());
        }
    }

    @Test
    @DisplayName("getWeComOAuthUrl 应委托给 WeComOAuthService.buildOAuthUrl")
    void getWeComOAuthUrl_delegatesToWeComOAuthService() {
        // Arrange
        String expectedUrl = "https://open.weixin.qq.com/connect/oauth2/authorize?appid=ww123&state=abc#wechat_redirect";
        when(weComOAuthService.buildOAuthUrl()).thenReturn(expectedUrl);

        // Act
        String result = authService.getWeComOAuthUrl();

        // Assert
        assertThat(result).isEqualTo(expectedUrl);
        verify(weComOAuthService).buildOAuthUrl();
    }
}
