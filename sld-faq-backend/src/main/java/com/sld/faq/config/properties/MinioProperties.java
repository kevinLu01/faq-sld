package com.sld.faq.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 对象存储配置属性
 * <p>
 * 对应 application.yml 中 {@code minio.*} 配置项。
 */
@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /** MinIO 服务地址，如 http://localhost:9000 */
    private String endpoint;

    /** 访问密钥（Access Key） */
    private String accessKey;

    /** 秘密密钥（Secret Key） */
    private String secretKey;

    /** 默认存储桶名称 */
    private String bucket;
}
