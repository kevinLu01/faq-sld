package com.sld.faq.module.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sld.faq.module.file.entity.KbChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * kb_chunk 表 Mapper
 */
@Mapper
public interface KbChunkMapper extends BaseMapper<KbChunk> {

    /**
     * 按文件 ID 查询所有 chunk，按 chunk_index 排序
     */
    @Select("SELECT * FROM kb_chunk WHERE file_id = #{fileId} ORDER BY chunk_index")
    List<KbChunk> selectByFileId(Long fileId);
}
