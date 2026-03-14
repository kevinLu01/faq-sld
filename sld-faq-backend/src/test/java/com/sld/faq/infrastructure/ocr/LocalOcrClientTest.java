package com.sld.faq.infrastructure.ocr;

import com.sld.faq.config.properties.OcrProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * LocalOcrClient 单元测试
 * <p>
 * 覆盖场景：
 * 1. 正常返回（success=true）应返回成功 OcrResult
 * 2. OCR 服务返回 success=false 应返回失败 OcrResult
 * 3. OCR 服务返回空响应应返回失败 OcrResult
 * 4. RestTemplate 抛异常应返回失败 OcrResult（不抛出）
 * 5. 响应中 text/markdown 为 null 时应默认空字符串
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LocalOcrClient 本地 OCR 客户端测试")
class LocalOcrClientTest {

    @Mock
    private RestTemplate mockRestTemplate;

    private LocalOcrClient localOcrClient;

    @BeforeEach
    void setUp() throws Exception {
        // 构建 OcrProperties
        OcrProperties properties = new OcrProperties();
        OcrProperties.Local local = new OcrProperties.Local();
        local.setBaseUrl("http://localhost:8866");
        local.setTimeout(Duration.ofSeconds(30));
        properties.setLocal(local);

        // 创建 LocalOcrClient，然后用反射注入 mock RestTemplate
        localOcrClient = new LocalOcrClient(properties);
        Field restTemplateField = LocalOcrClient.class.getDeclaredField("restTemplate");
        restTemplateField.setAccessible(true);
        restTemplateField.set(localOcrClient, mockRestTemplate);
    }

    @Test
    @DisplayName("OCR 服务正常返回 success=true 时应返回成功结果")
    void ocr_successResponse_returnsSuccessResult() {
        LocalOcrClient.OcrServiceResponse response = new LocalOcrClient.OcrServiceResponse();
        response.setSuccess(true);
        response.setText("识别出的文本");
        response.setMarkdown("| 表头 |\n| --- |\n| 内容 |");

        when(mockRestTemplate.postForObject(anyString(), any(), eq(LocalOcrClient.OcrServiceResponse.class)))
                .thenReturn(response);

        OcrResult result = localOcrClient.ocr(new byte[]{1, 2, 3}, "test.png");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getText()).isEqualTo("识别出的文本");
        assertThat(result.getMarkdown()).isEqualTo("| 表头 |\n| --- |\n| 内容 |");
    }

    @Test
    @DisplayName("OCR 服务返回 success=false 时应返回失败结果并包含错误信息")
    void ocr_failureResponse_returnsFailureResult() {
        LocalOcrClient.OcrServiceResponse response = new LocalOcrClient.OcrServiceResponse();
        response.setSuccess(false);
        response.setErrorMsg("模型加载失败");
        response.setText("");
        response.setMarkdown("");

        when(mockRestTemplate.postForObject(anyString(), any(), eq(LocalOcrClient.OcrServiceResponse.class)))
                .thenReturn(response);

        OcrResult result = localOcrClient.ocr(new byte[]{1, 2, 3}, "test.png");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).isEqualTo("模型加载失败");
    }

    @Test
    @DisplayName("OCR 服务返回 success=false 且无 errorMsg 时应使用默认错误信息")
    void ocr_failureResponseNoErrorMsg_returnsDefaultErrorMsg() {
        LocalOcrClient.OcrServiceResponse response = new LocalOcrClient.OcrServiceResponse();
        response.setSuccess(false);
        response.setErrorMsg(null);

        when(mockRestTemplate.postForObject(anyString(), any(), eq(LocalOcrClient.OcrServiceResponse.class)))
                .thenReturn(response);

        OcrResult result = localOcrClient.ocr(new byte[]{1, 2, 3}, "test.png");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("失败");
    }

    @Test
    @DisplayName("OCR 服务返回空响应时应返回失败结果")
    void ocr_nullResponse_returnsFailureResult() {
        when(mockRestTemplate.postForObject(anyString(), any(), eq(LocalOcrClient.OcrServiceResponse.class)))
                .thenReturn(null);

        OcrResult result = localOcrClient.ocr(new byte[]{1, 2, 3}, "test.png");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("空响应");
    }

    @Test
    @DisplayName("RestTemplate 抛出异常时应返回失败结果而不抛出")
    void ocr_restTemplateThrows_returnsFailureResult() {
        when(mockRestTemplate.postForObject(anyString(), any(), eq(LocalOcrClient.OcrServiceResponse.class)))
                .thenThrow(new RestClientException("Connection refused"));

        OcrResult result = localOcrClient.ocr(new byte[]{1, 2, 3}, "test.png");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("Connection refused");
    }

    @Test
    @DisplayName("响应中 text 和 markdown 为 null 时应默认为空字符串")
    void ocr_nullTextAndMarkdown_defaultsToEmptyString() {
        LocalOcrClient.OcrServiceResponse response = new LocalOcrClient.OcrServiceResponse();
        response.setSuccess(true);
        response.setText(null);
        response.setMarkdown(null);

        when(mockRestTemplate.postForObject(anyString(), any(), eq(LocalOcrClient.OcrServiceResponse.class)))
                .thenReturn(response);

        OcrResult result = localOcrClient.ocr(new byte[]{1, 2, 3}, "test.png");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getText()).isEqualTo("");
        assertThat(result.getMarkdown()).isEqualTo("");
    }

    @Test
    @DisplayName("响应中无 success 字段（null）时应按成功处理")
    void ocr_noSuccessField_treatedAsSuccess() {
        LocalOcrClient.OcrServiceResponse response = new LocalOcrClient.OcrServiceResponse();
        response.setSuccess(null);
        response.setText("一些文本");
        response.setMarkdown("一些 markdown");

        when(mockRestTemplate.postForObject(anyString(), any(), eq(LocalOcrClient.OcrServiceResponse.class)))
                .thenReturn(response);

        OcrResult result = localOcrClient.ocr(new byte[]{1, 2, 3}, "test.png");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getText()).isEqualTo("一些文本");
    }
}
