package com.sld.faq.module.auth.dto;

import lombok.Data;

/**
 * Mock 登录请求参数（仅开发环境使用）
 */
@Data
public class MockLoginRequest {

    /** 企业微信用户 ID */
    private String userId;

    /** 用户姓名 */
    private String name;

    /** 角色代码：ADMIN | REVIEWER | SUBMITTER */
    private String role;
}
