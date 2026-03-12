package com.sld.faq.module.auth.dto;

import com.sld.faq.module.user.vo.UserVO;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录响应 DTO
 */
@Data
@AllArgsConstructor
public class LoginResponse {

    /** Sa-Token 签发的 token */
    private String token;

    /** 当前登录用户信息 */
    private UserVO user;
}
