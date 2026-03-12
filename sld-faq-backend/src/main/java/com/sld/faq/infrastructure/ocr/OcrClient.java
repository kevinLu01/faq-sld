package com.sld.faq.infrastructure.ocr;

/**
 * OCR 客户端统一接口
 * <p>
 * 实现类可切换不同的 OCR 服务（本地 GOT-OCR、云端服务等），
 * 通过 {@code ocr.provider} 配置项控制激活哪个实现。
 */
public interface OcrClient {

    /**
     * 对文件字节数组执行 OCR 识别
     *
     * @param fileBytes 文件内容的字节数组
     * @param filename  原始文件名（含扩展名，用于推断文件类型）
     * @return OCR 识别结果，失败时返回 success=false 的结果，不抛出异常
     */
    OcrResult ocr(byte[] fileBytes, String filename);
}
