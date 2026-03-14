package com.sld.faq.infrastructure.ocr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NoOpOcrClient 单元测试
 * <p>
 * 验证 OCR 未启用时的兜底实现行为：
 * 1. 始终返回 success=false
 * 2. 错误信息包含"未启用"提示
 * 3. text 和 markdown 为空字符串
 */
@DisplayName("NoOpOcrClient 兜底 OCR 客户端测试")
class NoOpOcrClientTest {

    private final NoOpOcrClient noOpOcrClient = new NoOpOcrClient();

    @Test
    @DisplayName("调用 ocr 应始终返回失败结果")
    void ocr_alwaysReturnsFailure() {
        OcrResult result = noOpOcrClient.ocr(new byte[]{1, 2, 3}, "test.png");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("未启用");
    }

    @Test
    @DisplayName("失败结果中 text 和 markdown 应为空字符串")
    void ocr_failureResultHasEmptyTextAndMarkdown() {
        OcrResult result = noOpOcrClient.ocr(new byte[]{1, 2, 3}, "scan.pdf");

        assertThat(result.getText()).isEqualTo("");
        assertThat(result.getMarkdown()).isEqualTo("");
    }

    @Test
    @DisplayName("传入空字节数组和空文件名不应抛出异常")
    void ocr_emptyInputsDoNotThrow() {
        OcrResult result = noOpOcrClient.ocr(new byte[0], "");

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("传入 null 文件名不应抛出异常")
    void ocr_nullFilenameDoesNotThrow() {
        OcrResult result = noOpOcrClient.ocr(new byte[]{1}, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("未启用");
    }
}
