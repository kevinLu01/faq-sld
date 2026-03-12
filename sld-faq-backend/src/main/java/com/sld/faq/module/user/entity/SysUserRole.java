package com.sld.faq.module.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户角色关联实体，对应 sys_user_role 表（复合主键）
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    private Long userId;

    private Long roleId;
}
