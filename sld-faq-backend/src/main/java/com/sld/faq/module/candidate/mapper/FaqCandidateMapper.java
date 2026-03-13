package com.sld.faq.module.candidate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sld.faq.module.candidate.entity.FaqCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * faq_candidate 表 Mapper
 */
@Mapper
public interface FaqCandidateMapper extends BaseMapper<FaqCandidate> {

    /**
     * 分页查询（支持 status 和 fileId 过滤，按 created_at desc）
     * 动态 SQL 在 XML 中定义
     */
    Page<FaqCandidate> selectPage(Page<FaqCandidate> page,
                                  @Param("status") String status,
                                  @Param("fileId") Long fileId);

    /**
     * 加排他锁查询候选 FAQ（SELECT ... FOR UPDATE）
     * 必须在事务内调用，用于防止并发审核产生重复 FAQ。
     */
    @Select("SELECT * FROM faq_candidate WHERE id = #{id} FOR UPDATE")
    FaqCandidate selectForUpdate(@Param("id") Long id);

    /**
     * 统计某状态的数量
     */
    @Select("SELECT COUNT(*) FROM faq_candidate WHERE status = #{status}")
    long countByStatus(@Param("status") String status);
}
