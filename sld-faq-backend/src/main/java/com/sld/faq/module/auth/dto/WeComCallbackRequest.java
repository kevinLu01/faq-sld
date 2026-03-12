package com.sld.faq.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 企业微信 OAuth 回调请求参数
 */
@Data
public class WeComCallbackRequest {

    @NotBlank(message = "code 不能为空")
    private String code;

    @NotBlank(message = "state 不能为空")
    private String state;
}
