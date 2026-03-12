package com.sld.faq.infrastructure.ocr;

import lombok.Data;

/**
 * OCR 识别结果
 */
@Data
public class OcrResult {

    /** 识别出的纯文本内容 */
    private String text;

    /** 带表格结构的 Markdown 内容 */
    private String markdown;

    /** 是否识别成功 */
    private boolean success;

    /** 失败时的错误信息 */
    private String errorMsg;

    /**
     * 构建成功结果
     */
    public static OcrResult success(String text, String markdown) {
        OcrResult result = new OcrResult();
        result.setText(text);
        result.setMarkdown(markdown);
        result.setSuccess(true);
        return result;
    }

    /**
     * 构建失败结果
     */
    public static OcrResult failure(String errorMsg) {
        OcrResult result = new OcrResult();
        result.setSuccess(false);
        result.setErrorMsg(errorMsg);
        result.setText("");
        result.setMarkdown("");
        return result;
    }
}
