package com.sld.faq.module.product.dto;

import lombok.Data;

@Data
public class ProductCandidateDto {
    private String name;
    private String model;
    private String brand;
    private String specs;           // JSON 字符串，如 {"制冷量":"3.5kW"}
    private String compatModels;
    private String category;
    private String sourceSummary;
    private Double confidence;
}
