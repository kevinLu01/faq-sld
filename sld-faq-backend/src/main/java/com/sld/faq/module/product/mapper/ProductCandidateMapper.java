package com.sld.faq.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sld.faq.module.product.entity.ProductCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProductCandidateMapper extends BaseMapper<ProductCandidate> {

    @Select("SELECT * FROM product_candidate WHERE id = #{id} FOR UPDATE")
    ProductCandidate selectForUpdate(@Param("id") Long id);

    @Select({
        "<script>",
        "SELECT * FROM product_candidate",
        "<where>",
        "  <if test='status != null and status != \"\"'>",
        "    <choose>",
        "      <when test='status.contains(\",\")'>",
        "        AND status IN",
        "        <foreach item='s' collection='status.split(\",\")' open='(' separator=',' close=')'>#{s}</foreach>",
        "      </when>",
        "      <otherwise>AND status = #{status}</otherwise>",
        "    </choose>",
        "  </if>",
        "  <if test='fileId != null'>AND file_id = #{fileId}</if>",
        "</where>",
        "ORDER BY created_at DESC",
        "</script>"
    })
    Page<ProductCandidate> selectPage(Page<ProductCandidate> page,
                                      @Param("status") String status,
                                      @Param("fileId") Long fileId);
}
