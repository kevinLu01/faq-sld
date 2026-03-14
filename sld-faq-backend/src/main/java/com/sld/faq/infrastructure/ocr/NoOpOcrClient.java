package com.sld.faq.infrastructure.ocr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 空操作 OCR 客户端（兜底实现）
 * <p>
 * 当 {@code ocr.enabled=false}（或未配置）时激活，确保 {@link OcrClient} 始终有可用 bean，
 * 避免依赖注入失败。所有调用直接返回失败结果。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ocr.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpOcrClient implements OcrClient {

    @Override
    public OcrResult ocr(byte[] fileBytes, String filename) {
        log.debug("OCR 服务未启用，跳过识别: filename={}", filename);
        return OcrResult.failure("OCR 服务未启用");
    }
}
