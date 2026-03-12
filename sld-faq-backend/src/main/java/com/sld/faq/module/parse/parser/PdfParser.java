package com.sld.faq.module.parse.parser;

import com.sld.faq.infrastructure.ocr.OcrClient;
import com.sld.faq.infrastructure.ocr.OcrResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * PDF 文档解析器（Apache PDFBox 3.x）
 * <p>
 * 先尝试文本提取，若文本量极少（扫描版）则回退到 OCR 逐页识别。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfParser {

    private static final float OCR_DPI = 150f;
    private static final int SCAN_TEXT_THRESHOLD = 50;

    private final OcrClient ocrClient;

    /**
     * 解析 PDF 文件
     * 1. 用 Loader.loadPDF(bytes) 加载（PDFBox 3.x API）
     * 2. PDFTextStripper 提取文本
     * 3. 若提取文本 trim().length() < 50，认为是扫描版
     *    → 逐页转为图片（PDFRenderer.renderImageWithDPI），调 OcrClient.ocr
     * 4. 返回原始文本
     *
     * @param fileBytes PDF 文件字节数组
     * @return 提取的原始文本
     * @throws IOException 解析失败时抛出
     */
    public String parse(byte[] fileBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            if (text != null && text.trim().length() >= SCAN_TEXT_THRESHOLD) {
                log.debug("PDF 文本提取成功，字符数: {}", text.trim().length());
                return text;
            }

            // 文本量不足，认为是扫描版，逐页 OCR
            log.info("PDF 文本量不足（{}字），识别为扫描版，启动 OCR", text == null ? 0 : text.trim().length());
            return ocrByPage(document);
        }
    }

    /**
     * 逐页渲染为图片后调用 OCR 服务
     */
    private String ocrByPage(PDDocument document) throws IOException {
        PDFRenderer renderer = new PDFRenderer(document);
        int pageCount = document.getNumberOfPages();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < pageCount; i++) {
            BufferedImage image = renderer.renderImageWithDPI(i, OCR_DPI);
            byte[] imageBytes = toBytes(image);
            String filename = "page_" + (i + 1) + ".png";

            OcrResult result = ocrClient.ocr(imageBytes, filename);
            if (result.isSuccess() && result.getText() != null && !result.getText().isBlank()) {
                sb.append(result.getText()).append("\n\n");
            } else {
                log.warn("第 {} 页 OCR 失败或无结果: {}", i + 1, result.getErrorMsg());
            }
        }

        return sb.toString();
    }

    /**
     * 将 BufferedImage 转为 PNG 字节数组
     */
    private byte[] toBytes(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        }
    }
}
