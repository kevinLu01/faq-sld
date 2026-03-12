package com.sld.faq.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 统一捕获各类异常并转换为标准 {@link ApiResponse} 格式。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /**
     * Sa-Token 未登录异常
     */
    @ExceptionHandler(NotLoginException.class)
    public ApiResponse<Void> handleNotLoginException(NotLoginException e) {
        log.warn("未登录访问: {}", e.getMessage());
        return ApiResponse.error(401, "未登录");
    }

    /**
     * Sa-Token 无角色权限异常
     */
    @ExceptionHandler(NotRoleException.class)
    public ApiResponse<Void> handleNotRoleException(NotRoleException e) {
        log.warn("权限不足: {}", e.getMessage());
        return ApiResponse.error(403, "无权限");
    }

    /**
     * 请求参数校验异常（@Valid / @Validated）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", errorMsg);
        return ApiResponse.error(40001, errorMsg);
    }

    /**
     * 其他未知异常
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.error(500, "系统错误");
    }
}
