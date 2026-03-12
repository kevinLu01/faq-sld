package com.sld.faq.common;

/**
 * 业务异常
 * <p>
 * 用于在业务逻辑层抛出可预期的业务错误，由 {@link GlobalExceptionHandler} 统一捕获并返回 ApiResponse。
 */
public class BusinessException extends RuntimeException {

    /** 业务错误码 */
    private final int code;

    /**
     * 使用默认业务错误码 40000
     *
     * @param message 错误信息
     */
    public BusinessException(String message) {
        this(40000, message);
    }

    /**
     * 指定错误码和错误信息
     *
     * @param code    业务错误码
     * @param message 错误信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
