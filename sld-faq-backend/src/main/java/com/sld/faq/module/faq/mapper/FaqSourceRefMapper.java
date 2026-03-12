package com.sld.faq.module.faq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sld.faq.module.faq.entity.FaqSourceRef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * faq_source_ref 表 Mapper
 */
@Mapper
public interface FaqSourceRefMapper extends BaseMapper<FaqSourceRef> {

    @Select("SELECT * FROM faq_source_ref WHERE faq_id = #{faqId}")
    List<FaqSourceRef> selectByFaqId(@Param("faqId") Long faqId);
}
