package com.sld.faq.module.candidate.vo;

import lombok.Data;

/**
 * FAQ 候选视图对象
 */
@Data
public class CandidateVO {

    private Long id;

    private String question;

    private String answer;

    private String category;

    private String keywords;

    private String sourceSummary;

    private Double confidence;

    private String status;

    private String rejectReason;

    /**
     * kb_chunk.clean_content — 来源原文片段
     */
    private String sourceChunk;

    /**
     * kb_file.original_name — 来源文件名
     */
    private String fileName;

    private Long fileId;

    private String createdAt;
}
