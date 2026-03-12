package com.sld.faq.module.candidate.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * FAQ 候选实体，对应 faq_candidate 表
 */
@Data
@TableName("faq_candidate")
public class FaqCandidate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fileId;

    private Long chunkId;

    private String question;

    private String answer;

    private String category;

    private String keywords;

    private String sourceSummary;

    private Double confidence;

    /**
     * 候选状态：PENDING | APPROVED | REJECTED | MERGED
     */
    private String status;

    private String rejectReason;

    /**
     * status=MERGED 时关联的 faq_item id
     */
    private Long mergedFaqId;

    private Long reviewerId;

    private LocalDateTime reviewedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
