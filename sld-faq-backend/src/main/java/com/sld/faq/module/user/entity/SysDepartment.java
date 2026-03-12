package com.sld.faq.module.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门实体，对应 sys_department 表
 */
@Data
@TableName("sys_department")
public class SysDepartment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long parentId;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
