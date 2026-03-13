package com.sld.faq.infrastructure.llm;

import com.sld.faq.common.BusinessException;
import com.sld.faq.config.properties.LlmProperties;
import com.sld.faq.infrastructure.llm.dto.LlmMessage;
import com.sld.faq.infrastructure.llm.dto.LlmRequest;
import com.sld.faq.infrastructure.llm.dto.LlmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * LLM 客户端
 * <p>
 * 调用 OpenAI 兼容的 Chat Completion 接口，支持 Ollama、OpenAI、其他兼容服务。
 * 连接超时和读取超时均使用 {@link LlmProperties#getTimeout()} 配置。
 */
@Slf4j
@Component
public class LlmClient {

    private final LlmProperties llmProperties;
    private final RestTemplate restTemplate;

    public LlmClient(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
        int timeoutMs = (int) llmProperties.getTimeout().toMillis();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 发送单轮对话请求，返回模型输出的文本内容
     *
     * @param prompt 用户提示词
     * @return 模型输出的文本
     * @throws BusinessException 调用失败时抛出
     */
    public String chat(String prompt) {
        String url = llmProperties.getBaseUrl() + "/chat/completions";

        LlmRequest request = new LlmRequest(
                llmProperties.getModelName(),
                List.of(new LlmMessage("user", prompt)),
                0.3
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmProperties.getApiKey());

        HttpEntity<LlmRequest> entity = new HttpEntity<>(request, headers);

        try {
            log.debug("调用 LLM 接口: url={}, model={}", url, llmProperties.getModelName());
            LlmResponse response = restTemplate.postForObject(url, entity, LlmResponse.class);
            if (response == null) {
                throw new BusinessException("LLM 接口返回空响应");
            }
            String content = response.getFirstContent();
            log.debug("LLM 响应长度: {} chars", content.length());
            return content;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM 接口调用失败: url={}", url, e);
            throw new BusinessException("LLM 服务调用失败: " + e.getMessage());
        }
    }
}
