package com.sld.faq.module.parse;

import com.sld.faq.module.file.entity.KbFile;
import com.sld.faq.module.file.mapper.KbFileMapper;
import com.sld.faq.module.parse.parser.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文档解析服务入口
 * <p>
 * 按文件类型将解析任务分发到对应的解析器，并维护解析状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParseService {

    private final KbFileMapper kbFileMapper;
    private final PdfParser pdfParser;
    private final WordParser wordParser;
    private final ExcelParser excelParser;
    private final PlainTextParser plainTextParser;

    /**
     * 按文件类型分发解析，返回原始文本
     * <p>
     * 解析失败时更新 kb_file.parseStatus 为 FAILED，返回 null。
     *
     * @param kbFile    文件记录
     * @param fileBytes 文件字节内容
     * @return 原始文本，解析失败时返回 null
     */
    public String parse(KbFile kbFile, byte[] fileBytes) {
        String fileType = kbFile.getFileType().toLowerCase();
        updateParseStatus(kbFile.getId(), "PARSING", null);

        try {
            String rawText = switch (fileType) {
                case "pdf" -> pdfParser.parse(fileBytes);
                case "docx" -> wordParser.parse(fileBytes);
                case "xlsx" -> excelParser.parse(fileBytes);
                case "txt", "csv" -> plainTextParser.parse(fileBytes, fileType);
                default -> throw new UnsupportedOperationException("不支持的文件类型: " + fileType);
            };

            if (rawText == null || rawText.isBlank()) {
                log.warn("文件解析结果为空: fileId={}, type={}", kbFile.getId(), fileType);
                updateParseStatus(kbFile.getId(), "FAILED", "解析结果为空");
                return null;
            }

            log.info("文件解析成功: fileId={}, type={}, 字符数={}", kbFile.getId(), fileType, rawText.length());
            return rawText;

        } catch (Exception e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("文件解析异常: fileId={}, type={}", kbFile.getId(), fileType, e);
            updateParseStatus(kbFile.getId(), "FAILED", errMsg.length() > 500 ? errMsg.substring(0, 500) : errMsg);
            return null;
        }
    }

    /**
     * 更新文件解析状态
     */
    private void updateParseStatus(Long fileId, String status, String errorMsg) {
        KbFile update = new KbFile();
        update.setId(fileId);
        update.setParseStatus(status);
        update.setParseError(errorMsg);
        kbFileMapper.updateById(update);
    }
}
