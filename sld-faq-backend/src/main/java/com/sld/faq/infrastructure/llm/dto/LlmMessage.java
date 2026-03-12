package com.sld.faq.infrastructure.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 对话消息体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmMessage {

    /** 消息角色：system / user / assistant */
    private String role;

    /** 消息内容 */
    private String content;
}
