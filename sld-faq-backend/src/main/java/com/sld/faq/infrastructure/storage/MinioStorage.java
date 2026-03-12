package com.sld.faq.infrastructure.storage;

import com.sld.faq.common.BusinessException;
import com.sld.faq.config.properties.MinioProperties;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储封装
 * <p>
 * 提供文件上传、预签名下载 URL 生成、文件下载等功能。
 * 启动时自动检查目标 bucket 是否存在，不存在则创建。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioStorage {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    private static final DateTimeFormatter DATE_PATH_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * 应用启动时检查 bucket，不存在则创建
     */
    @PostConstruct
    public void initBucket() {
        String bucket = minioProperties.getBucket();
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build()
            );
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket 不存在，已自动创建: {}", bucket);
            } else {
                log.info("MinIO bucket 已存在: {}", bucket);
            }
        } catch (Exception e) {
            log.error("MinIO bucket 初始化失败: bucket={}", bucket, e);
            throw new RuntimeException("MinIO bucket 初始化失败", e);
        }
    }

    /**
     * 上传文件到 MinIO
     *
     * @param originalFilename 原始文件名（含扩展名）
     * @param inputStream      文件输入流
     * @param size             文件大小（字节）
     * @param contentType      MIME 类型
     * @return objectPath，格式：yyyy/MM/dd/uuid.ext（不含 bucket 前缀）
     */
    public String upload(String originalFilename, InputStream inputStream, long size, String contentType) {
        String bucket = minioProperties.getBucket();
        String objectPath = buildObjectPath(originalFilename);

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectPath)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build()
            );
            log.info("文件上传成功: bucket={}, objectPath={}", bucket, objectPath);
            return objectPath;
        } catch (Exception e) {
            log.error("文件上传失败: bucket={}, objectPath={}", bucket, objectPath, e);
            throw new BusinessException("文件上传到 MinIO 失败: " + e.getMessage());
        }
    }

    /**
     * 获取预签名下载 URL（有效期 1 小时）
     *
     * @param objectPath 对象路径（upload 方法返回值）
     * @return 预签名 HTTP URL
     */
    public String getPresignedUrl(String objectPath) {
        String bucket = minioProperties.getBucket();
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucket)
                            .object(objectPath)
                            .method(Method.GET)
                            .expiry(1, TimeUnit.HOURS)
                            .build()
            );
        } catch (Exception e) {
            log.error("获取预签名 URL 失败: bucket={}, objectPath={}", bucket, objectPath, e);
            throw new BusinessException("获取文件下载地址失败: " + e.getMessage());
        }
    }

    /**
     * 下载文件为 InputStream
     *
     * @param objectPath 对象路径（upload 方法返回值）
     * @return 文件输入流（调用方负责关闭）
     */
    public InputStream download(String objectPath) {
        String bucket = minioProperties.getBucket();
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectPath)
                            .build()
            );
        } catch (Exception e) {
            log.error("文件下载失败: bucket={}, objectPath={}", bucket, objectPath, e);
            throw new BusinessException("文件下载失败: " + e.getMessage());
        }
    }

    /**
     * 根据原始文件名生成存储路径
     * <p>
     * 格式：yyyy/MM/dd/uuid.ext
     */
    private String buildObjectPath(String originalFilename) {
        String datePath = LocalDate.now().format(DATE_PATH_FORMAT);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String ext = getFileExtension(originalFilename);
        return datePath + "/" + uuid + ext;
    }

    /**
     * 提取文件扩展名（含点号），如 ".pdf"
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }
}
