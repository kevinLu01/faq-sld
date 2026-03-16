package com.sld.faq.module.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sld.faq.module.product.dto.ProductCandidateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 产品 JSON 容错解析器（参照 FaqJsonParser 实现）
 * <p>
 * 解析 LLM 返回的 {"products":[...]} 格式，字段缺失时填默认值，
 * 彻底失败时返回空列表而不抛异常。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductJsonParser {

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("\\{[\\s\\S]*}", Pattern.DOTALL);

    private final ObjectMapper objectMapper;

    public List<ProductCandidateDto> parse(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return List.of();
        }

        JsonNode root = tryParse(llmOutput.strip());
        if (root == null) {
            Matcher matcher = JSON_BLOCK_PATTERN.matcher(llmOutput);
            if (matcher.find()) {
                root = tryParse(matcher.group());
            }
        }

        if (root == null) {
            log.warn("产品 LLM 输出无法解析为 JSON，已跳过。输出前200字: {}",
                    llmOutput.length() > 200 ? llmOutput.substring(0, 200) : llmOutput);
            return List.of();
        }

        JsonNode productsNode = root.get("products");
        if (productsNode == null || !productsNode.isArray() || productsNode.isEmpty()) {
            return List.of();
        }

        List<ProductCandidateDto> result = new ArrayList<>();
        for (JsonNode node : productsNode) {
            ProductCandidateDto dto = extractDto(node);
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }

    private ProductCandidateDto extractDto(JsonNode node) {
        String name = text(node, "name");
        String model = text(node, "model");

        // 产品名称和型号至少有一个才保留
        if (isBlank(name) && isBlank(model)) {
            return null;
        }

        ProductCandidateDto dto = new ProductCandidateDto();
        dto.setName(isBlank(name) ? "" : name.strip());
        dto.setModel(isBlank(model) ? "" : model.strip());

        String brand = text(node, "brand");
        dto.setBrand(isBlank(brand) ? "" : brand.strip());

        // specs: 可能是对象或字符串
        JsonNode specsNode = node.get("specs");
        if (specsNode != null && !specsNode.isNull()) {
            try {
                dto.setSpecs(objectMapper.writeValueAsString(specsNode));
            } catch (Exception e) {
                dto.setSpecs("{}");
            }
        } else {
            dto.setSpecs("{}");
        }

        String compatModels = text(node, "compat_models");
        dto.setCompatModels(isBlank(compatModels) ? "" : compatModels.strip());

        String category = text(node, "category");
        dto.setCategory(isBlank(category) ? "其他" : category.strip());

        String sourceSummary = text(node, "source_summary");
        dto.setSourceSummary(isBlank(sourceSummary) ? "" : sourceSummary.strip());

        JsonNode confNode = node.get("confidence");
        if (confNode != null && confNode.isNumber()) {
            dto.setConfidence(Math.max(0.0, Math.min(1.0, confNode.asDouble())));
        } else {
            dto.setConfidence(0.5);
        }

        return dto;
    }

    private JsonNode tryParse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n == null || n.isNull()) ? null : n.asText();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
