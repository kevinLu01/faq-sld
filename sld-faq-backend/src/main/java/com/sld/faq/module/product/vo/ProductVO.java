package com.sld.faq.module.product.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductVO {
    private Long id;
    private String name;
    private String model;
    private String brand;
    private Long categoryId;
    private String categoryName;
    private String specs;           // JSON 字符串
    private String compatModels;
    private String description;
    private Integer status;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
