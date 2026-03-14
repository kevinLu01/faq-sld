package com.sld.faq.module.parse.parser;

import com.sld.faq.infrastructure.ocr.OcrClient;
import com.sld.faq.infrastructure.ocr.OcrResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * PdfParser 单元测试
 * <p>
 * 覆盖场景：
 * 1. 文本 PDF（提取文本 >= 50 字）应直接返回文本，不走 OCR
 * 2. 扫描版 PDF（提取文本 < 50 字）应走 OCR 逐页识别
 * 3. OCR 返回 markdown 时应优先使用 markdown
 * 4. OCR 返回 markdown 为空时应 fallback 到 text
 * 5. OCR 失败时应跳过该页不抛异常
 * 6. 所有页 OCR 失败时应返回空字符串
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PdfParser PDF 解析器测试")
class PdfParserTest {

    @Mock
    private OcrClient ocrClient;

    @InjectMocks
    private PdfParser pdfParser;

    /**
     * 创建包含足够文本的 PDF（文本量 >= 50 字，不触发 OCR）
     */
    private byte[] createTextPdf(String text) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    /**
     * 创建空白 PDF（无文本层，模拟扫描版）
     */
    private byte[] createBlankPdf(int pageCount) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (int i = 0; i < pageCount; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    @DisplayName("文本 PDF（>=50 字）应直接返回提取文本，不调用 OCR")
    void parse_textPdf_returnsTextWithoutOcr() throws IOException {
        // 构造长度超过 50 字符的英文文本
        String longText = "This is a test document with enough text to pass the scan threshold check.";
        byte[] pdfBytes = createTextPdf(longText);

        String result = pdfParser.parse(pdfBytes);

        assertThat(result.trim()).contains("This is a test document");
        verifyNoInteractions(ocrClient);
    }

    @Test
    @DisplayName("扫描版 PDF（<50 字）应逐页调用 OCR 并返回 OCR 结果")
    void parse_scannedPdf_callsOcrPerPage() throws IOException {
        byte[] pdfBytes = createBlankPdf(2);

        when(ocrClient.ocr(any(byte[].class), eq("page_1.png")))
                .thenReturn(OcrResult.success("Page 1 text", ""));
        when(ocrClient.ocr(any(byte[].class), eq("page_2.png")))
                .thenReturn(OcrResult.success("Page 2 text", ""));

        String result = pdfParser.parse(pdfBytes);

        assertThat(result).contains("Page 1 text");
        assertThat(result).contains("Page 2 text");
        verify(ocrClient, times(2)).ocr(any(byte[].class), anyString());
    }

    @Test
    @DisplayName("OCR 返回 markdown 非空时应优先使用 markdown")
    void parse_scannedPdf_prefersMarkdown() throws IOException {
        byte[] pdfBytes = createBlankPdf(1);

        when(ocrClient.ocr(any(byte[].class), eq("page_1.png")))
                .thenReturn(OcrResult.success("plain text", "| col1 | col2 |\n| --- | --- |\n| a | b |"));

        String result = pdfParser.parse(pdfBytes);

        // 应使用 markdown 而非纯文本
        assertThat(result).contains("| col1 | col2 |");
        assertThat(result).doesNotContain("plain text");
    }

    @Test
    @DisplayName("OCR 返回 markdown 为空但 text 非空时应 fallback 到 text")
    void parse_scannedPdf_fallbackToTextWhenMarkdownBlank() throws IOException {
        byte[] pdfBytes = createBlankPdf(1);

        when(ocrClient.ocr(any(byte[].class), eq("page_1.png")))
                .thenReturn(OcrResult.success("OCR recognized text", ""));

        String result = pdfParser.parse(pdfBytes);

        assertThat(result).contains("OCR recognized text");
    }

    @Test
    @DisplayName("OCR 返回 markdown 为 null 但 text 非空时应 fallback 到 text")
    void parse_scannedPdf_fallbackToTextWhenMarkdownNull() throws IOException {
        byte[] pdfBytes = createBlankPdf(1);

        when(ocrClient.ocr(any(byte[].class), eq("page_1.png")))
                .thenReturn(OcrResult.success("fallback text content", null));

        String result = pdfParser.parse(pdfBytes);

        assertThat(result).contains("fallback text content");
    }

    @Test
    @DisplayName("某页 OCR 失败时应跳过该页，不影响其他页的结果")
    void parse_scannedPdf_ocrFailureSkipsPage() throws IOException {
        byte[] pdfBytes = createBlankPdf(3);

        when(ocrClient.ocr(any(byte[].class), eq("page_1.png")))
                .thenReturn(OcrResult.success("first page", ""));
        when(ocrClient.ocr(any(byte[].class), eq("page_2.png")))
                .thenReturn(OcrResult.failure("GPU 内存不足"));
        when(ocrClient.ocr(any(byte[].class), eq("page_3.png")))
                .thenReturn(OcrResult.success("third page", ""));

        String result = pdfParser.parse(pdfBytes);

        assertThat(result).contains("first page");
        assertThat(result).doesNotContain("GPU");
        assertThat(result).contains("third page");
    }

    @Test
    @DisplayName("所有页 OCR 失败时应返回空字符串")
    void parse_scannedPdf_allOcrFailed_returnsEmpty() throws IOException {
        byte[] pdfBytes = createBlankPdf(2);

        when(ocrClient.ocr(any(byte[].class), anyString()))
                .thenReturn(OcrResult.failure("服务不可用"));

        String result = pdfParser.parse(pdfBytes);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("单页空白 PDF 的 OCR 返回成功但内容为空时应不添加内容")
    void parse_scannedPdf_ocrSuccessButEmptyContent() throws IOException {
        byte[] pdfBytes = createBlankPdf(1);

        when(ocrClient.ocr(any(byte[].class), eq("page_1.png")))
                .thenReturn(OcrResult.success("", ""));

        String result = pdfParser.parse(pdfBytes);

        assertThat(result).isEmpty();
    }
}
