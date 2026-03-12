package com.sld.faq.module.faq.vo;

import lombok.Data;

/**
 * FAQ 来源引用视图对象
 */
@Data
public class SourceRefVO {

    /**
     * 来源文件名
     */
    private String fileName;

    /**
     * 来源 chunk 原文（截断前 200 字）
     */
    private String chunkContent;
}
