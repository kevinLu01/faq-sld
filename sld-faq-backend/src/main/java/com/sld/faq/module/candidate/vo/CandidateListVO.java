package com.sld.faq.module.candidate.vo;

import lombok.Data;

import java.util.List;

/**
 * FAQ 候选列表视图对象
 */
@Data
public class CandidateListVO {

    private long total;

    /**
     * 全局待审核数量（始终统计所有 PENDING 状态，不受过滤条件影响）
     */
    private long pendingCount;

    private List<CandidateVO> items;
}
