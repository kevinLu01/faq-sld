package com.sld.faq.module.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sld.faq.module.generate.dto.FaqCandidateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * FaqJsonParser 单元测试
 * <p>
 * 覆盖 LLM 输出 JSON 的多种容错解析场景：
 * 1. 标准格式正常解析
 * 2. JSON 前有额外文字时能提取
 * 3. 缺失字段使用默认值填充
 * 4. question/answer 为空时过滤
 * 5. 完全无效 JSON 返回空列表不抛异常
 * 6. faqs 数组为空时返回空列表
 */
@DisplayName("FaqJsonParser LLM 输出 JSON 解析测试")
class FaqJsonParserTest {

    private FaqJsonParser faqJsonParser;

    @BeforeEach
    void setUp() {
        faqJsonParser = new FaqJsonParser(new ObjectMapper());
    }

    @Test
    @DisplayName("标准格式 JSON 应正确解析为 FaqCandidateDto 列表")
    void parse_validJson_returnsCorrectFaqs() {
        String json = """
                {
                  "faqs": [
                    {
                      "question": "员工报销差旅费需要哪些材料？",
                      "answer": "需要提供出差申请单（已审批）、交通票据原件和住宿发票原件。",
                      "category": "差旅报销",
                      "keywords": "差旅,报销,材料",
                      "source_summary": "差旅报销管理办法第三章",
                      "confidence": 0.92
                    },
                    {
                      "question": "年假申请需要提前多少天提交？",
                      "answer": "员工请年假需提前三个工作日在 HR 系统中提交申请。",
                      "category": "年假政策",
                      "keywords": "年假,申请,工作日",
                      "source_summary": "员工手册年假章节",
                      "confidence": 0.88
                    }
                  ]
                }
                """;

        List<FaqCandidateDto> result = faqJsonParser.parse(json);

        assertThat(result).hasSize(2);

        FaqCandidateDto first = result.get(0);
        assertThat(first.getQuestion()).isEqualTo("员工报销差旅费需要哪些材料？");
        assertThat(first.getAnswer()).contains("出差申请单");
        assertThat(first.getCategory()).isEqualTo("差旅报销");
        assertThat(first.getKeywords()).isEqualTo("差旅,报销,材料");
        assertThat(first.getConfidence()).isEqualTo(0.92);

        FaqCandidateDto second = result.get(1);
        assertThat(second.getQuestion()).isEqualTo("年假申请需要提前多少天提交？");
        assertThat(second.getCategory()).isEqualTo("年假政策");
    }

    @Test
    @DisplayName("JSON 前有多余文字时应能提取 JSON 块并解析")
    void parse_jsonWithPrefixText_extractsCorrectly() {
        String llmOutput = """
                根据您提供的文档内容，我整理了以下常见问题：

                {
                  "faqs": [
                    {
                      "question": "试用期薪资是正式薪资的几折？",
                      "answer": "试用期薪资为正式薪资的 80%，试用期结束转正后按正式薪资发放。",
                      "category": "薪酬福利",
                      "keywords": "试用期,薪资,转正",
                      "confidence": 0.85
                    }
                  ]
                }
                """;

        List<FaqCandidateDto> result = faqJsonParser.parse(llmOutput);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getQuestion()).isEqualTo("试用期薪资是正式薪资的几折？");
        assertThat(result.get(0).getAnswer()).contains("80%");
    }

    @Test
    @DisplayName("category 缺失时应使用默认值 '其他'，confidence 缺失时应使用 0.5")
    void parse_missingFields_usesDefaults() {
        String json = """
                {
                  "faqs": [
                    {
                      "question": "公司是否提供午餐补贴？",
                      "answer": "公司提供每日 15 元的午餐补贴，在工资单中每月统一发放。"
                    }
                  ]
                }
                """;

        List<FaqCandidateDto> result = faqJsonParser.parse(json);

        assertThat(result).hasSize(1);
        FaqCandidateDto dto = result.get(0);
        assertThat(dto.getCategory()).isEqualTo("其他");
        assertThat(dto.getConfidence()).isEqualTo(0.5);
        assertThat(dto.getKeywords()).isEmpty();
    }

    @Test
    @DisplayName("question 或 answer 为空的条目应被过滤掉")
    void parse_emptyQuestionOrAnswer_filteredOut() {
        String json = """
                {
                  "faqs": [
                    {
                      "question": "员工可以申请哪些假期？",
                      "answer": "包括年假、病假、婚假、产假、陪产假等法定假期。",
                      "category": "假期管理",
                      "confidence": 0.90
                    },
                    {
                      "question": "",
                      "answer": "这条没有问题，应该被过滤",
                      "category": "测试",
                      "confidence": 0.70
                    },
                    {
                      "question": "这条没有答案",
                      "answer": "",
                      "category": "测试",
                      "confidence": 0.65
                    },
                    {
                      "question": null,
                      "answer": "answer 存在但 question 是 null",
                      "category": "测试"
                    }
                  ]
                }
                """;

        List<FaqCandidateDto> result = faqJsonParser.parse(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getQuestion()).isEqualTo("员工可以申请哪些假期？");
    }

    @Test
    @DisplayName("完全无效的 JSON 应返回空列表，不抛任何异常")
    void parse_invalidJson_returnsEmptyList() {
        assertThatCode(() -> {
            List<FaqCandidateDto> result = faqJsonParser.parse("这不是任何 JSON 内容，只是普通文字段落。");
            assertThat(result).isEmpty();
        }).doesNotThrowAnyException();

        assertThatCode(() -> {
            List<FaqCandidateDto> result = faqJsonParser.parse("{broken json: [}");
            assertThat(result).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("faqs 数组为空时应返回空列表")
    void parse_emptyFaqsArray_returnsEmptyList() {
        String json = """
                {
                  "faqs": []
                }
                """;

        List<FaqCandidateDto> result = faqJsonParser.parse(json);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 和空字符串输入应返回空列表")
    void parse_nullOrBlankInput_returnsEmptyList() {
        assertThat(faqJsonParser.parse(null)).isEmpty();
        assertThat(faqJsonParser.parse("")).isEmpty();
        assertThat(faqJsonParser.parse("   ")).isEmpty();
    }

    @Test
    @DisplayName("confidence 值应被限制在 0.0 到 1.0 之间")
    void parse_confidenceClampedToValidRange() {
        String json = """
                {
                  "faqs": [
                    {
                      "question": "置信度超过 1.0 的情况",
                      "answer": "正确答案内容",
                      "confidence": 1.5
                    },
                    {
                      "question": "置信度小于 0.0 的情况",
                      "answer": "正确答案内容二",
                      "confidence": -0.3
                    }
                  ]
                }
                """;

        List<FaqCandidateDto> result = faqJsonParser.parse(json);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getConfidence()).isLessThanOrEqualTo(1.0);
        assertThat(result.get(1).getConfidence()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("faqs 节点不存在时应返回空列表")
    void parse_missingFaqsNode_returnsEmptyList() {
        String json = """
                {
                  "result": "ok",
                  "items": []
                }
                """;

        List<FaqCandidateDto> result = faqJsonParser.parse(json);

        assertThat(result).isEmpty();
    }
}
