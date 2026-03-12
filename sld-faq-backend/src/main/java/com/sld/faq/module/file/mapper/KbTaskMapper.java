package com.sld.faq.module.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sld.faq.module.file.entity.KbTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * kb_task 表 Mapper
 */
@Mapper
public interface KbTaskMapper extends BaseMapper<KbTask> {

    /**
     * 查询文件最新的任务记录
     */
    @Select("SELECT * FROM kb_task WHERE file_id = #{fileId} ORDER BY id DESC LIMIT 1")
    KbTask selectLatestByFileId(Long fileId);
}
