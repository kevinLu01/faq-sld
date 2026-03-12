package com.sld.faq.module.generate.dto;

import lombok.Data;

/**
 * LLM 输出的 FAQ 候选数据传输对象
 */
@Data
public class FaqCandidateDto {

    private String question;

    private String answer;

    private String category;

    private String keywords;

    /** LLM 生成的来源摘要描述 */
    private String sourceSummary;

    /** 置信度 0.0~1.0 */
    private Double confidence;
}
