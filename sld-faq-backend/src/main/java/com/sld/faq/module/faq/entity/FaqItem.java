package com.sld.faq.module.faq.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 正式 FAQ 条目实体，对应 faq_item 表
 */
@Data
@TableName("faq_item")
public class FaqItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String question;

    private String answer;

    private Long categoryId;

    private String keywords;

    /**
     * 发布状态：1 已发布，0 已下线
     */
    private Integer status;

    private Integer viewCount;

    private Long publisherId;

    private LocalDateTime publishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
