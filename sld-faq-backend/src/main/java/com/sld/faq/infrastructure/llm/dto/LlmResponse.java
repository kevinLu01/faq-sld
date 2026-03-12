package com.sld.faq.infrastructure.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * LLM Chat Completion 响应体（OpenAI 兼容格式）
 * <p>
 * 忽略未知字段，避免不同 LLM 提供商返回字段差异导致的反序列化异常。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmResponse {

    /** 响应选项列表，通常取 choices[0] */
    private List<Choice> choices;

    /**
     * 从响应中提取第一个 choice 的文本内容
     *
     * @return 模型输出的文本内容，若无则返回空字符串
     */
    public String getFirstContent() {
        if (choices != null && !choices.isEmpty()) {
            Choice choice = choices.get(0);
            if (choice.getMessage() != null) {
                return choice.getMessage().getContent();
            }
        }
        return "";
    }

    /**
     * 单个响应候选
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private LlmMessage message;
        private String finishReason;
    }
}
