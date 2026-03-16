package com.sld.faq.module.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("product_source_ref")
public class ProductSourceRef {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;
    private Long candidateId;
    private Long fileId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
