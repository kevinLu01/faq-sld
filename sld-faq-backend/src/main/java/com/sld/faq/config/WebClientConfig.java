package com.sld.faq.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP 客户端配置
 * <p>
 * 提供通用 {@link RestTemplate} Bean，供企业微信 OAuth 等基础设施组件注入使用。
 * LLM 客户端和 OCR 客户端各自通过 {@link org.springframework.boot.web.client.RestTemplateBuilder}
 * 构建独立实例以便配置不同超时。
 */
@Configuration
public class WebClientConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
