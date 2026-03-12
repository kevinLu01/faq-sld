package com.sld.faq.infrastructure.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * LLM Chat Completion 请求体（OpenAI 兼容格式）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmRequest {

    /** 模型名称 */
    private String model;

    /** 对话消息列表 */
    private List<LlmMessage> messages;

    /** 温度参数，控制随机性（0.0 ~ 1.0） */
    private double temperature = 0.3;
}
