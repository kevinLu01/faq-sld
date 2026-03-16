package com.sld.faq.module.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.sld.faq.common.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "product_candidate", autoResultMap = true)
public class ProductCandidate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fileId;
    private Long chunkId;

    private String name;
    private String model;
    private String brand;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String specs;           // JSONB: {"制冷量":"3.5kW","电压":"220V"}

    private String compatModels;    // 适配机型，逗号分隔
    private String category;
    private String sourceSummary;
    private Double confidence;

    private String status;          // PENDING / APPROVED / REJECTED
    private String rejectReason;

    private Long reviewerId;
    private LocalDateTime reviewedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
