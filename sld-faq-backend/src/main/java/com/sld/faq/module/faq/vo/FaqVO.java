package com.sld.faq.module.faq.vo;

import lombok.Data;

import java.util.List;

/**
 * 正式 FAQ 视图对象
 */
@Data
public class FaqVO {

    private Long id;

    private String question;

    private String answer;

    private String categoryName;

    private String keywords;

    /**
     * 发布状态：1 已发布，0 已下线
     */
    private Integer status;

    private Integer viewCount;

    private String publishedAt;

    /**
     * 来源引用列表，仅详情接口返回
     */
    private List<SourceRefVO> sourceRefs;
}
