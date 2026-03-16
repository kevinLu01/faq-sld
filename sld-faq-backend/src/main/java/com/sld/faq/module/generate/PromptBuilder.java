package com.sld.faq.module.generate;

import org.springframework.stereotype.Component;

/**
 * Prompt 构建器
 * <p>
 * 根据 PromptMode 和 chunk 内容生成发送给 LLM 的完整提示词。
 */
@Component
public class PromptBuilder {

    private static final String DOCUMENT_TEMPLATE =
            "你是一个专业的知识库整理助手，服务于制造业（空调配件）企业。\n" +
            "以下是从企业内部文档中提取的文本片段：\n\n" +
            "---\n" +
            "{CONTENT}\n" +
            "---\n\n" +
            "请基于以上内容提取 0~3 条有价值的 FAQ。\n\n" +
            "要求：\n" +
            "1. 只能基于原文，不能编造原文中没有的信息\n" +
            "2. question 用真实用户可能提问的方式表达，简洁明确\n" +
            "3. category 从以下选项选择：产品规格、安装维修、故障排查、售后政策、操作说明、其他\n" +
            "4. keywords 为 3~5 个关键词，逗号分隔\n" +
            "5. confidence 为 0.0~1.0 的置信度\n" +
            "6. 如果文本不含有效知识点，返回空数组\n\n" +
            "严格按以下 JSON 输出，不要输出任何其他内容：\n" +
            "{\"faqs\":[{\"question\":\"...\",\"answer\":\"...\",\"category\":\"...\",\"keywords\":\"...\",\"source_summary\":\"...\",\"confidence\":0.9}]}";

    private static final String PRODUCT_TEMPLATE =
            "你是一个专业的产品档案整理助手，服务于制造业（空调配件）代理企业。\n" +
            "以下是从产品说明书或产品资料中提取的文本内容：\n\n" +
            "---\n" +
            "{CONTENT}\n" +
            "---\n\n" +
            "请从以上内容提取 0~3 条产品档案信息。\n\n" +
            "要求：\n" +
            "1. 只能基于原文，不能编造原文中没有的信息\n" +
            "2. model 为产品型号（如 KFR-35GW），name 为产品名称（如 变频空调挂机）\n" +
            "3. brand 为品牌/厂家名称\n" +
            "4. specs 为规格参数对象，键为参数名，值为参数值（如 {\"制冷量\":\"3500W\",\"电压\":\"220V\"}）\n" +
            "5. compat_models 为适配主机型号，多个用逗号分隔，无则留空\n" +
            "6. category 从以下选项选择：空调整机、室外机配件、室内机配件、制冷配件、电控配件、其他\n" +
            "7. confidence 为 0.0~1.0 的置信度\n" +
            "8. 如果文本不含有效产品信息，返回空数组\n\n" +
            "严格按以下 JSON 输出，不要输出任何其他内容：\n" +
            "{\"products\":[{\"name\":\"...\",\"model\":\"...\",\"brand\":\"...\",\"specs\":{\"制冷量\":\"...\"},\"compat_models\":\"...\",\"category\":\"...\",\"source_summary\":\"...\",\"confidence\":0.9}]}";

    private static final String CONVERSATION_TEMPLATE =
            "你是一个专业的知识库整理助手，服务于制造业（空调配件）企业。\n" +
            "以下是企业内部工作群的聊天记录片段：\n\n" +
            "---\n" +
            "{CONTENT}\n" +
            "---\n\n" +
            "请从对话中识别有价值的问答对，整理为 0~3 条 FAQ。\n\n" +
            "要求：\n" +
            "1. 只能基于对话中真实出现的问题和解答，不能编造\n" +
            "2. 过滤闲聊、表情、无实质内容的消息\n" +
            "3. question 还原提问者真实意图，用标准问句表达\n" +
            "4. 如果对话中没有明确问答对，返回空数组\n\n" +
            "严格按以下 JSON 输出，不要输出任何其他内容：\n" +
            "{\"faqs\":[{\"question\":\"...\",\"answer\":\"...\",\"category\":\"...\",\"keywords\":\"...\",\"source_summary\":\"...\",\"confidence\":0.85}]}";

    /**
     * 根据 mode 和 chunkContent 构建完整 prompt
     *
     * @param chunkContent 文本块内容
     * @param mode         Prompt 模式
     * @return 完整提示词
     */
    public String build(String chunkContent, PromptMode mode) {
        String template = switch (mode) {
            case DOCUMENT -> DOCUMENT_TEMPLATE;
            case CONVERSATION -> CONVERSATION_TEMPLATE;
            case PRODUCT -> PRODUCT_TEMPLATE;
        };
        return template.replace("{CONTENT}", chunkContent);
    }
}
