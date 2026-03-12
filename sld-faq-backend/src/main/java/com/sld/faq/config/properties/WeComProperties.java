package com.sld.faq.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 企业微信 OAuth 配置属性
 * <p>
 * 对应 application.yml 中 {@code wecom.*} 配置项。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wecom")
public class WeComProperties {

    /** 企业 ID */
    private String corpId;

    /** 应用 Secret */
    private String corpSecret;

    /** 应用 ID（AgentId） */
    private String agentId;

    /** OAuth 回调地址（需在企业微信后台配置） */
    private String redirectUri;

    /**
     * 是否开启 Mock 登录（本地开发用，生产环境必须为 false）
     */
    private boolean mockLogin = false;
}
