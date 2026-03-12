package com.sld.faq.module.faq.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * FAQ 分类实体，对应 faq_category 表
 */
@Data
@TableName("faq_category")
public class FaqCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long parentId;

    private Integer sortOrder;

    private LocalDateTime createdAt;
}
