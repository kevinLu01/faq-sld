package com.sld.faq.module.faq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sld.faq.module.faq.entity.FaqItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * faq_item 表 Mapper
 */
@Mapper
public interface FaqItemMapper extends BaseMapper<FaqItem> {

    /**
     * 关键词搜索分页（question 或 keywords 包含关键词，status=1）
     * 动态 SQL 在 XML 中定义
     */
    Page<FaqItem> searchPage(Page<FaqItem> page,
                             @Param("keyword") String keyword,
                             @Param("categoryId") Long categoryId);
}
