package com.sld.faq.module.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.sld.faq.common.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "product_item", autoResultMap = true)
public class ProductItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String model;
    private String brand;
    private Long categoryId;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String specs;

    private String compatModels;
    private String description;
    private Integer status;         // 1=上架 0=下架

    private Long publisherId;
    private LocalDateTime publishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
