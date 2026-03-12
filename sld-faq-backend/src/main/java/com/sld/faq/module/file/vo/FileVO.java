package com.sld.faq.module.file.vo;

import lombok.Data;

/**
 * 文件信息视图对象
 */
@Data
public class FileVO {

    private Long id;

    private String originalName;

    private String fileType;

    private Long fileSize;

    /** 解析状态：PENDING|PARSING|SUCCESS|FAILED|SCAN_PDF */
    private String parseStatus;

    private Integer chunkCount;

    /** 格式化后的创建时间字符串 */
    private String createdAt;

    /** 最新任务状态，可为 null */
    private TaskStatusVO latestTask;
}
