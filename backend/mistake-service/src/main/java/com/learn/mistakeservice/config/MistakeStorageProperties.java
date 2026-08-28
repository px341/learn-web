package com.learn.mistakeservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/** Garage S3 兼容接口及错题图片预签名配置。 */
@Validated
@ConfigurationProperties(prefix = "storage.s3")
public record MistakeStorageProperties(
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
