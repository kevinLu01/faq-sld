package com.sld.faq.infrastructure.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sld.faq.config.properties.OcrProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * 本地 GOT-OCR 2.0 服务客户端
 * <p>
 * 通过 HTTP multipart/form-data POST 调用本地 FastAPI OCR 服务。
 * 仅在 {@code ocr.enabled=true} 时激活，作为默认主要实现（@Primary）。
 */
@Slf4j
@Primary
@Component
@ConditionalOnProperty(name = "ocr.enabled", havingValue = "true")
public class LocalOcrClient implements OcrClient {

    private final OcrProperties ocrProperties;
    private final RestTemplate restTemplate;

    public LocalOcrClient(OcrProperties ocrProperties, RestTemplateBuilder restTemplateBuilder) {
        this.ocrProperties = ocrProperties;
        OcrProperties.Local localConfig = ocrProperties.getLocal();
        this.restTemplate = restTemplateBuilder
                .connectTimeout(localConfig.getTimeout())
                .readTimeout(localConfig.getTimeout())
                .build();
    }

    /**
     * 调用本地 OCR 服务识别文件
     *
     * @param fileBytes 文件字节内容
     * @param filename  文件名（含扩展名）
     * @return OCR 结果，异常时返回 success=false，不抛出异常
     */
    @Override
    public OcrResult ocr(byte[] fileBytes, String filename) {
        String url = ocrProperties.getLocal().getBaseUrl() + "/ocr";
        log.debug("调用本地 OCR 服务: url={}, filename={}, size={}bytes", url, filename, fileBytes.length);

        try {
            // 构建 multipart 请求体
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // 调用 OCR 服务
            OcrServiceResponse response = restTemplate.postForObject(url, requestEntity, OcrServiceResponse.class);

            if (response == null) {
                log.warn("OCR 服务返回空响应: filename={}", filename);
                return OcrResult.failure("OCR 服务返回空响应");
            }

            log.debug("OCR 识别完成: filename={}, textLength={}", filename,
                    response.getText() != null ? response.getText().length() : 0);
            return OcrResult.success(
                    response.getText() != null ? response.getText() : "",
                    response.getMarkdown() != null ? response.getMarkdown() : ""
            );

        } catch (Exception e) {
            log.error("OCR 服务调用失败: url={}, filename={}", url, filename, e);
            return OcrResult.failure("OCR 识别失败: " + e.getMessage());
        }
    }

    /**
     * OCR 服务响应 JSON 对应的 DTO
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OcrServiceResponse {
        /** 识别出的纯文本 */
        private String text;
        /** 带表格结构的 Markdown */
        private String markdown;
    }
}
