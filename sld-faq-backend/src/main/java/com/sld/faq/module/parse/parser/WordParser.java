package com.sld.faq.module.parse.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Word (.docx) 文档解析器（Apache POI）
 * <p>
 * 遍历 body elements，保持段落和表格的原始顺序，表格转为文本格式。
 */
@Slf4j
@Component
public class WordParser {

    /**
     * 解析 .docx 文件
     * 1. XWPFDocument 加载
     * 2. 遍历 body elements（保持段落和表格的原始顺序）：
     *    - XWPFParagraph → getText()
     *    - XWPFTable → 转为 "[表格]\n列1 | 列2 | ..." 格式
     * 3. 拼接为完整文本，段落间用 \n\n 分隔
     *
     * @param fileBytes .docx 文件字节数组
     * @return 提取的原始文本
     * @throws IOException 解析失败时抛出
     */
    public String parse(byte[] fileBytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(fileBytes))) {
            StringBuilder sb = new StringBuilder();
            List<IBodyElement> elements = document.getBodyElements();

            for (IBodyElement element : elements) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    if (text != null && !text.isBlank()) {
                        sb.append(text).append("\n\n");
                    }
                } else if (element instanceof XWPFTable table) {
                    sb.append(tableToText(table)).append("\n\n");
                }
            }

            return sb.toString().strip();
        }
    }

    /**
     * 将表格转为纯文本格式：
     * [表格]
     * 列1 | 列2 | 列3
     * 值1 | 值2 | 值3
     */
    private String tableToText(XWPFTable table) {
        StringBuilder sb = new StringBuilder("[表格]\n");
        List<XWPFTableRow> rows = table.getRows();

        for (XWPFTableRow row : rows) {
            List<XWPFTableCell> cells = row.getTableCells();
            String rowText = cells.stream()
                    .map(cell -> cell.getText().replace("\n", " ").strip())
                    .collect(Collectors.joining(" | "));
            sb.append(rowText).append("\n");
        }

        return sb.toString().strip();
    }
}
