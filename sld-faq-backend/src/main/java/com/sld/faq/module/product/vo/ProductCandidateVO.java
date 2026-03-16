package com.sld.faq.module.product.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductCandidateVO {
    private Long id;
    private Long fileId;
    private String fileName;
    private String name;
    private String model;
    private String brand;
    private String specs;           // JSON 字符串
    private String compatModels;
    private String category;
    private String sourceSummary;
    private Double confidence;
    private String status;
    private String rejectReason;
    private LocalDateTime createdAt;
}
