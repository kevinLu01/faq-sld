package com.sld.faq.common;

import lombok.Data;

/**
 * 统一 API 响应体
 * <p>
 * 约定：code=0 表示成功，4xxxx 表示业务错误，401 未登录，403 无权限，500 系统错误
 */
@Data
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;

    private ApiResponse() {
    }

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功，带数据
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    /**
     * 成功，无数据
     */
    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    /**
     * 业务失败，指定 code 和 message
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    /**
     * 系统错误（code=500）
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(500, message, null);
    }
}
