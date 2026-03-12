package com.sld.faq.module.generate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sld.faq.module.generate.dto.FaqCandidateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 输出的 JSON 容错解析器
 * <p>
 * 对模型输出进行多级容错处理，尽量提取有效的 FAQ 候选数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaqJsonParser {

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("\\{[\\s\\S]*}", Pattern.DOTALL);

    private final ObjectMapper objectMapper;

    /**
     * 容错 JSON 解析器
     * 1. 尝试直接 JSON 解析
     * 2. 失败则用正则提取第一个 {...} 块再解析
     * 3. 解析成功后检查字段，缺失字段用默认值填充：
     *    - question/answer 为空 → 跳过该条
     *    - category 为空 → "其他"
     *    - keywords 为空 → ""
     *    - confidence 为空 → 0.5
     * 4. 彻底失败 → log.warn，返回空列表，不抛异常
     *
     * @param llmOutput LLM 输出的原始文本
     * @return 解析出的 FAQ 候选列表（question/answer 为空的条目已过滤）
     */
    public List<FaqCandidateDto> parse(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return List.of();
        }

        // 第一次：直接解析
        JsonNode root = tryParse(llmOutput.strip());

        // 第二次：正则提取 {...} 块
        if (root == null) {
            Matcher matcher = JSON_BLOCK_PATTERN.matcher(llmOutput);
            if (matcher.find()) {
                root = tryParse(matcher.group());
            }
        }

        if (root == null) {
            log.warn("LLM 输出无法解析为 JSON，已跳过。输出前200字: {}",
                    llmOutput.length() > 200 ? llmOutput.substring(0, 200) : llmOutput);
            return List.of();
        }

        // 提取 faqs 数组
        JsonNode faqsNode = root.get("faqs");
        if (faqsNode == null || !faqsNode.isArray() || faqsNode.isEmpty()) {
            return List.of();
        }

        List<FaqCandidateDto> result = new ArrayList<>();
        for (JsonNode node : faqsNode) {
            FaqCandidateDto dto = extractDto(node);
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }

    /**
     * 从单个 JSON 节点提取 FAQ 候选，缺失字段填默认值
     *
     * @return 若 question 或 answer 为空则返回 null
     */
    private FaqCandidateDto extractDto(JsonNode node) {
        String question = textOrNull(node, "question");
        String answer = textOrNull(node, "answer");

        if (isBlank(question) || isBlank(answer)) {
            return null;
        }

        FaqCandidateDto dto = new FaqCandidateDto();
        dto.setQuestion(question.strip());
        dto.setAnswer(answer.strip());

        String category = textOrNull(node, "category");
        dto.setCategory(isBlank(category) ? "其他" : category.strip());

        String keywords = textOrNull(node, "keywords");
        dto.setKeywords(isBlank(keywords) ? "" : keywords.strip());

        String sourceSummary = textOrNull(node, "source_summary");
        dto.setSourceSummary(sourceSummary != null ? sourceSummary.strip() : "");

        JsonNode confNode = node.get("confidence");
        if (confNode != null && confNode.isNumber()) {
            double conf = confNode.asDouble();
            dto.setConfidence(Math.max(0.0, Math.min(1.0, conf)));
        } else {
            dto.setConfidence(0.5);
        }

        return dto;
    }

    /**
     * 尝试用 Jackson 解析 JSON 字符串，失败返回 null
     */
    private JsonNode tryParse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        return n.asText();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
