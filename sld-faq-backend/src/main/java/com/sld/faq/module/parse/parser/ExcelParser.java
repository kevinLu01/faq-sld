package com.sld.faq.module.parse.parser;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.read.metadata.ReadSheet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Excel (.xlsx) 文档解析器（EasyExcel）
 * <p>
 * 读取所有 sheet，每个 sheet 转为文本表格格式。
 */
@Slf4j
@Component
public class ExcelParser {

    /**
     * 解析 .xlsx 文件
     * 使用 EasyExcel 读取所有 sheet，每个 sheet 转为文本表格：
     * [Sheet: 表名]
     * 列1 | 列2 | ...
     * 值1 | 值2 | ...
     *
     * @param fileBytes .xlsx 文件字节数组
     * @return 提取的原始文本
     * @throws IOException 解析失败时抛出
     */
    public String parse(byte[] fileBytes) throws IOException {
        // 先获取所有 sheet 信息
        List<ReadSheet> sheets;
        try {
            sheets = EasyExcel.read(new ByteArrayInputStream(fileBytes))
                    .build()
                    .excelExecutor()
                    .sheetList();
        } catch (Exception e) {
            log.error("读取 Excel sheet 列表失败", e);
            throw new IOException("读取 Excel 失败: " + e.getMessage(), e);
        }

        StringBuilder sb = new StringBuilder();

        for (ReadSheet sheet : sheets) {
            String sheetName = sheet.getSheetName() != null ? sheet.getSheetName() : "Sheet" + sheet.getSheetNo();
            SheetDataListener listener = new SheetDataListener();

            try {
                EasyExcel.read(new ByteArrayInputStream(fileBytes), listener)
                        .sheet(sheet.getSheetNo())
                        .doRead();
            } catch (Exception e) {
                log.warn("读取 Sheet[{}] 失败: {}", sheetName, e.getMessage());
                continue;
            }

            List<Map<Integer, String>> rows = listener.getRows();
            if (rows.isEmpty()) {
                continue;
            }

            sb.append("[Sheet: ").append(sheetName).append("]\n");
            for (Map<Integer, String> row : rows) {
                if (row.isEmpty()) {
                    continue;
                }
                int maxCol = row.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
                String rowText = java.util.stream.IntStream.rangeClosed(0, maxCol)
                        .mapToObj(i -> {
                            String val = row.get(i);
                            return val != null ? val.replace("\n", " ").strip() : "";
                        })
                        .collect(Collectors.joining(" | "));
                sb.append(rowText).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString().strip();
    }

    /**
     * EasyExcel 行数据监听器，使用 Map<Integer, String> 接收每行数据
     */
    private static class SheetDataListener extends AnalysisEventListener<Map<Integer, String>> {

        private final List<Map<Integer, String>> rows = new ArrayList<>();

        @Override
        public void invoke(Map<Integer, String> data, AnalysisContext context) {
            if (data != null && !data.isEmpty()) {
                rows.add(new LinkedHashMap<>(data));
            }
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            // nothing
        }

        public List<Map<Integer, String>> getRows() {
            return rows;
        }
    }
}
