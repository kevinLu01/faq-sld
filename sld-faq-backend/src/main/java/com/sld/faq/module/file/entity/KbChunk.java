package com.sld.faq.module.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sld.faq.config.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文本块实体，对应 kb_chunk 表
 */
@Data
@TableName(value = "kb_chunk", autoResultMap = true)
public class KbChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fileId;

    private Integer chunkIndex;

    private String rawContent;

    private String cleanContent;

    private Integer tokenCount;

    /** 来源元数据，存 JSON，如页码、段落标题等 */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String metadata;

    private LocalDateTime createdAt;
}
