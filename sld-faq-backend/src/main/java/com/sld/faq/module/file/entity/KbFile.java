package com.sld.faq.module.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文件实体，对应 kb_file 表
 */
@Data
@TableName("kb_file")
public class KbFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String originalName;

    private String minioPath;

    private Long fileSize;

    /** 文件类型：pdf|docx|xlsx|txt|csv */
    private String fileType;

    /** 解析状态：PENDING|PARSING|SUCCESS|FAILED|SCAN_PDF */
    private String parseStatus = "PENDING";

    private String parseError;

    private Integer chunkCount = 0;

    private Long submitterId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
