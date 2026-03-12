package com.sld.faq.module.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统角色实体，对应 sys_role 表
 */
@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色代码：ADMIN | REVIEWER | SUBMITTER */
    private String code;

    private String name;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
