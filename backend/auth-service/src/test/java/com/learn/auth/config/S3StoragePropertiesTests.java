package com.learn.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class S3StoragePropertiesTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(StorageConfiguration.class)
            .withPropertyValues(
                    "storage.s3.endpoint=http://garage:3900",
                    "storage.s3.public-endpoint=https://objects.example.com",
                    "storage.s3.region=garage",
                    "storage.s3.bucket=mistake-images",
                    "storage.s3.access-key=test-access-key",
                    "storage.s3.secret-key=test-secret-key",
                    "storage.s3.path-style-access=true",
                    "storage.s3.presigned-url-ttl=10m"
            );

    @Test
    void bindsInternalAndPublicEndpointsSeparately() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(S3StorageProperties.class);

            S3StorageProperties properties = context.getBean(S3StorageProperties.class);
            assertThat(properties.endpoint()).isEqualTo(URI.create("http://garage:3900"));
            assertThat(properties.publicEndpoint()).isEqualTo(URI.create("https://objects.example.com"));
            assertThat(properties.region()).isEqualTo("garage");
            assertThat(properties.bucket()).isEqualTo("mistake-images");
            assertThat(properties.pathStyleAccess()).isTrue();
            assertThat(properties.presignedUrlTtl()).isEqualTo(Duration.ofMinutes(10));
        });
    }
}
