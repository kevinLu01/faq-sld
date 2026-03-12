package com.sld.faq.module.candidate.dto;

import lombok.Data;

/**
 * 审核请求 DTO
 */
@Data
public class ReviewRequest {

    /**
     * 驳回原因（reject 时使用）
     */
    private String reason;

    /**
     * 编辑后的问题（edit-approve 时使用）
     */
    private String question;

    /**
     * 编辑后的答案（edit-approve 时使用）
     */
    private String answer;

    /**
     * 合并目标 FAQ id（merge 时使用）
     */
    private Long targetFaqId;
}
