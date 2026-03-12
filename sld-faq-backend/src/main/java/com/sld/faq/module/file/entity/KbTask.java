package com.sld.faq.module.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库任务实体，对应 kb_task 表
 */
@Data
@TableName("kb_task")
public class KbTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fileId;

    /** 任务类型：PARSE | GENERATE */
    private String taskType;

    /** 任务状态：PENDING|RUNNING|SUCCESS|FAILED */
    private String status = "PENDING";

    /** 进度 0~100 */
    private Integer progress = 0;

    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
