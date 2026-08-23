package com.learn.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册对象存储配置。
 *
 * <p>后续接入 AWS SDK 时，S3Client 和 S3Presigner 应分别使用服务端 Endpoint
 * 与公开 Endpoint，避免将 Docker 内部地址返回给浏览器。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(S3StorageProperties.class)
public class StorageConfiguration {
}
