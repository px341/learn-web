package com.learn.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/**
 * Garage S3 兼容接口的连接和预签名配置。
 *
 * <p>{@code endpoint} 供 Auth Service 上传、删除对象使用；
 * {@code publicEndpoint} 用于生成返回给浏览器的预签名 URL。服务运行在 Docker
 * 网络时，前者可以是 {@code http://garage:3900}，后者必须是浏览器可访问的地址。</p>
 *
 * <p>该配置只描述 S3 API，不允许业务服务直接访问 Garage 的数据目录。</p>
 *
 * @param endpoint 服务端访问 Garage S3 API 的地址
 * @param publicEndpoint 浏览器访问预签名对象的公开地址
 * @param region S3 Region，当前 Garage 配置为 {@code garage}
 * @param bucket 私有 Bucket 名称
 * @param accessKey S3 Access Key，只能通过配置注入，不得写入数据库或响应
 * @param secretKey S3 Secret Key，只能通过配置注入，不得写入数据库或日志
 * @param pathStyleAccess 是否强制使用 Path-style URL
 * @param presignedUrlTtl 预签名 URL 有效期
 */
@Validated
@ConfigurationProperties(prefix = "storage.s3")
public record S3StorageProperties(
        @NotNull URI endpoint,
        @NotNull URI publicEndpoint,
        @NotBlank String region,
        @NotBlank String bucket,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        boolean pathStyleAccess,
        @NotNull Duration presignedUrlTtl
) {
}
