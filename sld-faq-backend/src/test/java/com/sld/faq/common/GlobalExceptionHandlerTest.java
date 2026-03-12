package com.sld.faq.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GlobalExceptionHandler 单元测试
 * <p>
 * 直接调用 handler 方法，验证各异常类型到 ApiResponse 的映射：
 * 1. BusinessException → 对应 code + message
 * 2. MethodArgumentNotValidException → 40001 + 字段校验错误信息拼接
 * 3. 未知 Exception → 500 + "系统错误"
 * 4. NotLoginException → 401 + "未登录"
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler 全局异常处理测试")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private BindingResult bindingResult;

    @Mock
    private MethodParameter methodParameter;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // ========== BusinessException ==========

    @Test
    @DisplayName("BusinessException 应返回对应的 code 和 message")
    void handleBusinessException_returns400CodeAndMessage() {
        BusinessException ex = new BusinessException(40004, "文件不存在，请检查文件 ID 是否正确");

        ApiResponse<Void> response = handler.handleBusinessException(ex);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(40004);
        assertThat(response.getMessage()).isEqualTo("文件不存在，请检查文件 ID 是否正确");
        assertThat(response.getData()).isNull();
    }

    @Test
    @DisplayName("默认 code（40000）的 BusinessException 应正确处理")
    void handleBusinessException_defaultCode_returns40000() {
        BusinessException ex = new BusinessException("候选 FAQ 状态非 PENDING，当前状态=APPROVED");

        ApiResponse<Void> response = handler.handleBusinessException(ex);

        assertThat(response.getCode()).isEqualTo(40000);
        assertThat(response.getMessage()).contains("APPROVED");
    }

    @Test
    @DisplayName("code=403 的 BusinessException 应正确返回 403 和禁用提示")
    void handleBusinessException_403Code_returnsForbiddenMessage() {
        BusinessException ex = new BusinessException(403, "mock登录已禁用");

        ApiResponse<Void> response = handler.handleBusinessException(ex);

        assertThat(response.getCode()).isEqualTo(403);
        assertThat(response.getMessage()).isEqualTo("mock登录已禁用");
    }

    // ========== MethodArgumentNotValidException ==========

    @Test
    @DisplayName("参数校验异常应返回 40001 并拼接所有字段错误信息")
    void handleValidationException_returnsFieldErrors() {
        List<FieldError> fieldErrors = List.of(
                new FieldError("weComCallbackRequest", "code", "code 不能为空"),
                new FieldError("weComCallbackRequest", "state", "state 不能为空")
        );

        when(bindingResult.getFieldErrors()).thenReturn(fieldErrors);

        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(methodParameter, bindingResult);

        ApiResponse<Void> response = handler.handleMethodArgumentNotValidException(ex);

        assertThat(response.getCode()).isEqualTo(40001);
        assertThat(response.getMessage()).contains("code 不能为空");
        assertThat(response.getMessage()).contains("state 不能为空");
        // 两个错误信息之间用 "; " 分隔
        assertThat(response.getMessage()).contains("; ");
    }

    @Test
    @DisplayName("只有一个字段校验错误时应直接返回该错误信息（无分号）")
    void handleValidationException_singleFieldError_returnsDirectMessage() {
        List<FieldError> fieldErrors = List.of(
                new FieldError("mockLoginRequest", "userId", "用户 ID 不能为空")
        );

        when(bindingResult.getFieldErrors()).thenReturn(fieldErrors);

        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(methodParameter, bindingResult);

        ApiResponse<Void> response = handler.handleMethodArgumentNotValidException(ex);

        assertThat(response.getCode()).isEqualTo(40001);
        assertThat(response.getMessage()).isEqualTo("用户 ID 不能为空");
    }

    // ========== 未知 Exception ==========

    @Test
    @DisplayName("未知 RuntimeException 应返回 500 和 '系统错误'")
    void handleGenericException_returns500() {
        Exception ex = new RuntimeException("数据库连接池耗尽");

        ApiResponse<Void> response = handler.handleException(ex);

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).isEqualTo("系统错误");
        assertThat(response.getData()).isNull();
    }

    @Test
    @DisplayName("NullPointerException 也应被兜底处理并返回 500")
    void handleGenericException_nullPointerException_returns500() {
        Exception ex = new NullPointerException("空指针异常");

        ApiResponse<Void> response = handler.handleException(ex);

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).isEqualTo("系统错误");
    }

    // ========== Sa-Token 异常 ==========

    @Test
    @DisplayName("NotLoginException 应返回 401 和 '未登录'")
    void handleNotLoginException_returns401() {
        // 使用 Mockito mock NotLoginException，避免依赖其具体构造签名
        NotLoginException ex = mock(NotLoginException.class);

        ApiResponse<Void> response = handler.handleNotLoginException(ex);

        assertThat(response.getCode()).isEqualTo(401);
        assertThat(response.getMessage()).isEqualTo("未登录");
    }

    @Test
    @DisplayName("NotRoleException 应返回 403 和 '无权限'")
    void handleNotRoleException_returns403() {
        // NotRoleException(roleCode) 表示当前登录用户缺少指定角色
        NotRoleException ex = new NotRoleException("ADMIN");

        ApiResponse<Void> response = handler.handleNotRoleException(ex);

        assertThat(response.getCode()).isEqualTo(403);
        assertThat(response.getMessage()).isEqualTo("无权限");
    }
}
