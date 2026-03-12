package com.sld.faq.module.faq.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * FAQ 来源引用实体，对应 faq_source_ref 表
 */
@Data
@TableName("faq_source_ref")
public class FaqSourceRef {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long faqId;

    private Long candidateId;

    private Long chunkId;

    private Long fileId;

    private LocalDateTime createdAt;
}
