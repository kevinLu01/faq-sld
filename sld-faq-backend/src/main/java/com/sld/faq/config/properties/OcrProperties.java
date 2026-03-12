package com.sld.faq.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * OCR 服务配置属性
 * <p>
 * 对应 application.yml 中 {@code ocr.*} 配置项。
 * 通过 {@code ocr.provider} 可扩展多个 OCR 实现。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ocr")
public class OcrProperties {

    /**
     * OCR 服务提供者，默认使用本地 GOT-OCR 服务
     */
    private String provider = "local";

    /**
     * 是否启用 OCR 功能
     */
    private boolean enabled = true;

    /**
     * 本地 OCR 服务配置
     */
    private Local local = new Local();

    /**
     * 本地部署的 OCR 服务（GOT-OCR 2.0 FastAPI）配置
     */
    @Data
    public static class Local {

        /** OCR 服务地址，如 http://localhost:8866 */
        private String baseUrl;

        /** 请求超时时间，默认 30 秒 */
        private Duration timeout = Duration.ofSeconds(30);
    }
}
