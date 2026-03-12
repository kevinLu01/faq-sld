package com.sld.faq.module.user.vo;

import lombok.Data;

import java.util.List;

/**
 * 用户视图对象
 */
@Data
public class UserVO {

    private Long id;

    private String name;

    private String avatar;

    private String mobile;

    private List<String> roles;

    private String department;
}
