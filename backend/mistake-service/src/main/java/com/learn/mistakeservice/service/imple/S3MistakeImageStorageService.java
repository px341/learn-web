package com.learn.mistakeservice.service.imple;

import com.learn.mistakeservice.config.MistakeStorageProperties;
import com.learn.mistakeservice.exception.MistakeStorageException;
import com.learn.mistakeservice.model.PresignedImage;
import com.learn.mistakeservice.service.MistakeImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class S3MistakeImageStorageService implements MistakeImageStorageService {

    private final S3Presigner mistakeS3Presigner;
    private final MistakeStorageProperties properties;
    private final Clock clock = Clock.systemUTC();

    @Override
    public PresignedImage createReadUrl(String objectKey) {
        try {
            Instant expiresAt = clock.instant().plus(properties.presignedUrlTtl());
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build();
            GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                    .signatureDuration(properties.presignedUrlTtl())
                    .getObjectRequest(getObjectRequest)
                    .build();
            String url = mistakeS3Presigner.presignGetObject(request).url().toString();
            return new PresignedImage(url, expiresAt);
        } catch (RuntimeException exception) {
            throw new MistakeStorageException("错题图片访问地址生成失败", exception);
        }
    }
}
