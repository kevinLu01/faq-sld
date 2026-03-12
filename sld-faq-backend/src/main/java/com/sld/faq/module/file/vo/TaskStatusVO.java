package com.sld.faq.module.file.vo;

import lombok.Data;

/**
 * 任务状态视图对象
 */
@Data
public class TaskStatusVO {

    private Long id;

    /** 任务状态：PENDING|RUNNING|SUCCESS|FAILED */
    private String status;

    /** 进度 0~100 */
    private Integer progress;

    private String errorMsg;
}
