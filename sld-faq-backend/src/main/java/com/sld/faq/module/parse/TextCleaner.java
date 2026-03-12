package com.sld.faq.module.parse;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 文本清洗组件
 * <p>
 * 对从文档中提取的原始文本进行规范化处理，提升 LLM 输入质量。
 */
@Component
public class TextCleaner {

    /** 匹配纯数字页码行，如 "1"、"- 1 -"、"Page 1"、"第1页" 等 */
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile(
            "(?m)^[\\s\\-–—]*(?:Page|页|第)?\\s*[\\dIVXLCDMivxlcdm]+\\s*(?:页|of\\s*\\d+)?[\\s\\-–—]*$"
    );

    /** 匹配超过两个连续换行 */
    private static final Pattern EXCESSIVE_NEWLINES = Pattern.compile("\\n{3,}");

    /** 匹配段内孤立换行（单个 \n，下一行不以标点、空白、特殊符号开头） */
    private static final Pattern INLINE_BREAK = Pattern.compile("\\n(?![\\s\\p{P}\\[（【])");

    /**
     * 清洗规则：
     * 1. 去连续空白行（超过 2 个换行合并为 2 个）
     * 2. 去页码行（单行纯数字/罗马数字，如 "- 1 -"、"Page 1"）
     * 3. 合并段内断行（单个 \n 且下一行不以标点/空白开头，合并为空格）
     * 4. 去首尾空白
     *
     * @param rawText 原始文本
     * @return 清洗后的文本
     */
    public String clean(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        // 统一换行符
        String text = rawText.replace("\r\n", "\n").replace("\r", "\n");

        // 1. 去页码行
        text = PAGE_NUMBER_PATTERN.matcher(text).replaceAll("");

        // 2. 去连续空白行（>2 个换行合并为 2 个）
        text = EXCESSIVE_NEWLINES.matcher(text).replaceAll("\n\n");

        // 3. 合并段内断行（单个 \n，下一行不以标点/空白开头，合并为空格）
        text = INLINE_BREAK.matcher(text).replaceAll(" ");

        // 4. 去首尾空白
        text = text.strip();

        return text;
    }
}
