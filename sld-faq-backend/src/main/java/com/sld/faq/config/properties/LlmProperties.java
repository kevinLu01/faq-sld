package com.sld.faq.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * LLM（大语言模型）配置属性
 * <p>
 * 兼容 OpenAI API 格式，通过 {@code llm.*} 进行配置。
 * 可通过修改 base-url 切换不同的 LLM 服务（Ollama / OpenAI / 其他）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** API 基础地址，如 http://localhost:11434/v1 */
    private String baseUrl;

    /** API 密钥 */
    private String apiKey;

    /** 模型名称，如 qwen2.5:14b */
    private String modelName;

    /** 请求超时时间，默认 60 秒 */
    private Duration timeout = Duration.ofSeconds(60);
}
