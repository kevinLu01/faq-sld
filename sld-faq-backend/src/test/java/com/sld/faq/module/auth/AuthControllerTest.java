package com.sld.faq.module.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sld.faq.common.BusinessException;
import com.sld.faq.common.GlobalExceptionHandler;
import com.sld.faq.module.auth.dto.LoginResponse;
import com.sld.faq.module.auth.dto.MockLoginRequest;
import com.sld.faq.module.auth.dto.WeComCallbackRequest;
import com.sld.faq.module.user.UserService;
import com.sld.faq.module.user.vo.UserVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController MockMvc 测试
 * <p>
 * 覆盖认证 Controller 的 HTTP 层行为：
 * 1. GET /api/auth/wecom/url 返回 200 和 redirectUrl
 * 2. POST /api/auth/wecom/callback 正常返回登录响应
 * 3. POST /api/auth/wecom/callback code/state 为空时返回参数校验错误
 * 4. POST /api/auth/mock-login 正常返回
 * <p>
 * 使用 @ContextConfiguration 只加载 Controller 和 GlobalExceptionHandler，
 * 避免加载 SaTokenConfig（需要 Redis/ObjectMapper 等外部依赖）。
 */
@WebMvcTest(controllers = AuthController.class)
@ContextConfiguration(classes = {AuthController.class, GlobalExceptionHandler.class})
@DisplayName("AuthController HTTP 接口测试")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserService userService;

    // ========== GET /api/auth/wecom/url ==========

    @Test
    @DisplayName("GET /api/auth/wecom/url 应返回 200 和 OAuth 授权 URL")
    void getWeComUrl_returns200WithUrl() throws Exception {
        String expectedUrl = "https://open.weixin.qq.com/connect/oauth2/authorize" +
                "?appid=ww123456&redirect_uri=http%3A%2F%2Flocalhost%2Fauth%2Fcallback" +
                "&response_type=code&scope=snsapi_base&agentid=1000001&state=abc#wechat_redirect";

        when(authService.getWeComOAuthUrl()).thenReturn(expectedUrl);

        mockMvc.perform(get("/api/auth/wecom/url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(expectedUrl));
    }

    // ========== POST /api/auth/wecom/callback ==========

    @Test
    @DisplayName("POST /api/auth/wecom/callback 正常 code+state 应返回 200 和 token")
    void callback_validRequest_returns200WithToken() throws Exception {
        WeComCallbackRequest req = new WeComCallbackRequest();
        req.setCode("valid_oauth_code_12345");
        req.setState("valid_csrf_state_abcde");

        UserVO userVO = new UserVO();
        userVO.setId(101L);
        userVO.setName("张三");
        userVO.setRoles(List.of("SUBMITTER"));

        LoginResponse loginResponse = new LoginResponse("sa-token-xyz789", userVO);

        when(authService.wecomCallback("valid_oauth_code_12345", "valid_csrf_state_abcde"))
                .thenReturn(loginResponse);

        mockMvc.perform(post("/api/auth/wecom/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("sa-token-xyz789"))
                .andExpect(jsonPath("$.data.user.name").value("张三"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("SUBMITTER"));
    }

    @Test
    @DisplayName("POST /api/auth/wecom/callback code 为空时应返回参数校验错误（40001）")
    void callback_missingCode_returns400() throws Exception {
        // code 为空字符串，违反 @NotBlank
        String body = "{\"code\":\"\",\"state\":\"some_valid_state\"}";

        mockMvc.perform(post("/api/auth/wecom/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("POST /api/auth/wecom/callback state 为空时应返回参数校验错误（40001）")
    void callback_missingState_returnsValidationError() throws Exception {
        String body = "{\"code\":\"valid_code\",\"state\":\"\"}";

        mockMvc.perform(post("/api/auth/wecom/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("POST /api/auth/wecom/callback state 校验失败时应返回业务错误码（40010）")
    void callback_invalidState_returnsBusinessError() throws Exception {
        WeComCallbackRequest req = new WeComCallbackRequest();
        req.setCode("some_code");
        req.setState("forged_state");

        when(authService.wecomCallback(anyString(), anyString()))
                .thenThrow(new BusinessException(40010, "OAuth state 无效或已过期，请重新发起登录"));

        mockMvc.perform(post("/api/auth/wecom/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40010))
                .andExpect(jsonPath("$.message").value("OAuth state 无效或已过期，请重新发起登录"));
    }

    // ========== POST /api/auth/mock-login ==========

    @Test
    @DisplayName("POST /api/auth/mock-login 正常请求应返回 200 和 token")
    void mockLogin_returns200() throws Exception {
        MockLoginRequest req = new MockLoginRequest();
        req.setUserId("DevUser_001");
        req.setName("开发测试用户");
        req.setRole("REVIEWER");

        UserVO userVO = new UserVO();
        userVO.setId(202L);
        userVO.setName("开发测试用户");
        userVO.setRoles(List.of("REVIEWER"));

        LoginResponse loginResponse = new LoginResponse("mock-sa-token-abc123", userVO);

        when(authService.mockLogin("DevUser_001", "开发测试用户", "REVIEWER"))
                .thenReturn(loginResponse);

        mockMvc.perform(post("/api/auth/mock-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("mock-sa-token-abc123"))
                .andExpect(jsonPath("$.data.user.name").value("开发测试用户"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("REVIEWER"));
    }

    @Test
    @DisplayName("POST /api/auth/mock-login mock 关闭时应返回 403 业务错误")
    void mockLogin_whenMockDisabled_returns403Error() throws Exception {
        MockLoginRequest req = new MockLoginRequest();
        req.setUserId("AnyUser");
        req.setName("任意用户");
        req.setRole("ADMIN");

        when(authService.mockLogin(anyString(), anyString(), anyString()))
                .thenThrow(new BusinessException(403, "mock登录已禁用"));

        mockMvc.perform(post("/api/auth/mock-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("mock登录已禁用"));
    }
}
