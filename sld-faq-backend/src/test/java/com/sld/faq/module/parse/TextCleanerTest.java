package com.sld.faq.module.parse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * TextCleaner 单元测试
 * <p>
 * 覆盖文本清洗的核心规则：
 * 1. 连续空白行合并
 * 2. 页码行清除
 * 3. 首尾空白去除
 * 4. 边界输入（null / 空字符串）
 * 5. 正常内容不被误清理
 */
@DisplayName("TextCleaner 文本清洗测试")
class TextCleanerTest {

    private TextCleaner textCleaner;

    @BeforeEach
    void setUp() {
        textCleaner = new TextCleaner();
    }

    @Test
    @DisplayName("超过两个连续换行应被合并为两个换行")
    void clean_removesExcessiveBlankLines() {
        // 4 个换行 → 2 个换行
        String input = "第一段内容\n\n\n\n第二段内容";
        String result = textCleaner.clean(input);

        assertThat(result).doesNotContain("\n\n\n");
        assertThat(result).contains("第一段内容");
        assertThat(result).contains("第二段内容");
        // 验证两者之间最多两个换行
        assertThat(result).matches("(?s)第一段内容\\n{1,2}第二段内容");
    }

    @Test
    @DisplayName("纯数字行 / 页码行应被清理掉")
    void clean_removesPageNumbers() {
        // "- 1 -" 格式页码
        String withDashPageNum = "前一段落\n- 1 -\n后一段落";
        String result1 = textCleaner.clean(withDashPageNum);
        assertThat(result1).doesNotContain("- 1 -");
        assertThat(result1).contains("前一段落");
        assertThat(result1).contains("后一段落");

        // "Page 1" 格式
        String withPageKeyword = "前置内容\nPage 1\n后置内容";
        String result2 = textCleaner.clean(withPageKeyword);
        assertThat(result2).doesNotContain("Page 1");

        // "第1页" 格式
        String withChinesePage = "段落文字\n第1页\n继续段落";
        String result3 = textCleaner.clean(withChinesePage);
        assertThat(result3).doesNotContain("第1页");

        // 纯数字行（常见于 PDF 提取）
        String withBareNumber = "内容行\n3\n内容行二";
        String result4 = textCleaner.clean(withBareNumber);
        assertThat(result4).doesNotContain("\n3\n");
    }

    @Test
    @DisplayName("首尾空白字符应被去除")
    void clean_trimsLeadingAndTrailingWhitespace() {
        String input = "   \n\n  员工手册第一章：入职须知  \n\n  ";
        String result = textCleaner.clean(input);

        assertThat(result).doesNotStartWith(" ");
        assertThat(result).doesNotEndWith(" ");
        assertThat(result).doesNotStartWith("\n");
        assertThat(result).doesNotEndWith("\n");
        assertThat(result).contains("员工手册第一章：入职须知");
    }

    @Test
    @DisplayName("空字符串输入应返回空字符串，不抛异常")
    void clean_handlesEmptyInput() {
        assertThatCode(() -> {
            String result = textCleaner.clean("");
            assertThat(result).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null 输入应返回空字符串，不抛异常")
    void clean_handlesNullInput() {
        assertThatCode(() -> {
            String result = textCleaner.clean(null);
            assertThat(result).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("仅包含空白的字符串应返回空字符串")
    void clean_handlesBlankOnlyInput() {
        String result = textCleaner.clean("   \n\n\t  ");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("正常业务段落内容不应被误清理")
    void clean_preservesMeaningfulContent() {
        String input = "一、报销流程说明\n\n" +
                "员工因公出差产生的差旅费用，需在出差返回后 7 个工作日内完成报销申请。" +
                "超期未申报的费用，财务部门有权不予受理。\n\n" +
                "二、所需材料\n\n" +
                "1. 出差申请单（已审批）\n" +
                "2. 交通票据原件（火车票、机票、出租车发票）\n" +
                "3. 住宿发票原件";

        String result = textCleaner.clean(input);

        assertThat(result).contains("报销流程说明");
        assertThat(result).contains("7 个工作日");
        assertThat(result).contains("所需材料");
        assertThat(result).contains("交通票据原件");
        assertThat(result).contains("住宿发票原件");
    }

    @Test
    @DisplayName("Windows 换行符 \\r\\n 应被统一为 \\n 处理")
    void clean_normalizesWindowsLineEndings() {
        String input = "第一段内容\r\n\r\n\r\n\r\n第二段内容";
        String result = textCleaner.clean(input);

        assertThat(result).doesNotContain("\r");
        assertThat(result).contains("第一段内容");
        assertThat(result).contains("第二段内容");
        assertThat(result).doesNotContain("\n\n\n");
    }
}
