package com.sld.faq.module.parse.parser;

import lombok.extern.slf4j.Slf4j;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯文本 (txt/csv) 文档解析器
 * <p>
 * 使用 juniversalchardet 检测编码，再以检测到的编码读取文本。
 * CSV 按行读取，每行用 " | " 拼接字段。
 */
@Slf4j
@Component
public class PlainTextParser {

    /**
     * 解析 txt 或 csv 文件
     * 1. 用 juniversalchardet 检测编码
     * 2. 按检测编码读取文本
     * 3. csv：按行读取，每行用 " | " 拼接字段
     *
     * @param fileBytes 文件字节数组
     * @param fileType  文件类型（"txt" 或 "csv"）
     * @return 提取的原始文本
     * @throws IOException 解析失败时抛出
     */
    public String parse(byte[] fileBytes, String fileType) throws IOException {
        String charset = detectCharset(fileBytes);
        Charset encoding = parseCharset(charset);

        if ("csv".equalsIgnoreCase(fileType)) {
            return parseCsv(fileBytes, encoding);
        } else {
            return new String(fileBytes, encoding);
        }
    }

    /**
     * 使用 juniversalchardet 检测字节数组的字符编码
     *
     * @return 检测到的编码名，未检测到则返回 null
     */
    private String detectCharset(byte[] bytes) {
        UniversalDetector detector = new UniversalDetector(null);
        int chunkSize = Math.min(bytes.length, 4096);
        detector.handleData(bytes, 0, chunkSize);
        detector.dataEnd();
        String detected = detector.getDetectedCharset();
        detector.reset();
        log.debug("检测到文件编码: {}", detected);
        return detected;
    }

    /**
     * 将编码字符串转为 Charset，失败则降级为 UTF-8
     */
    private Charset parseCharset(String charsetName) {
        if (charsetName == null || charsetName.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(charsetName);
        } catch (Exception e) {
            log.warn("不支持的编码 {}，降级为 UTF-8", charsetName);
            return StandardCharsets.UTF_8;
        }
    }

    /**
     * 解析 CSV 文件：按行读取，每行字段用 " | " 拼接
     */
    private String parseCsv(byte[] bytes, Charset charset) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(bytes), charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 简单按逗号分割（不处理带引号的字段内换行）
                String[] fields = line.split(",", -1);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < fields.length; i++) {
                    if (i > 0) {
                        sb.append(" | ");
                    }
                    // 去除字段两端的引号和空白
                    String field = fields[i].strip();
                    if (field.startsWith("\"") && field.endsWith("\"") && field.length() >= 2) {
                        field = field.substring(1, field.length() - 1).replace("\"\"", "\"");
                    }
                    sb.append(field);
                }
                lines.add(sb.toString());
            }
        }
        return String.join("\n", lines);
    }
}
