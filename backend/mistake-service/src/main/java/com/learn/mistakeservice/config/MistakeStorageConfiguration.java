package com.learn.mistakeservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** 为私有错题图片注册使用浏览器可访问地址的 S3 预签名器。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MistakeStorageProperties.class)
public class MistakeStorageConfiguration {

    @Bean
    public S3Presigner mistakeS3Presigner(MistakeStorageProperties properties) {
        return S3Presigner.builder()
                .endpointOverride(properties.publicEndpoint())
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                properties.accessKey(),
                                properties.secretKey()
                        )
                ))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.pathStyleAccess())
                        .build())
                .build();
    }
}
