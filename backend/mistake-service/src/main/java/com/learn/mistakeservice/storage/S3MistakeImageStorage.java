package com.learn.mistakeservice.storage;

import com.learn.mistakeservice.config.MistakeStorageProperties;
import com.learn.mistakeservice.exception.MistakeStorageException;
import com.learn.mistakeservice.model.PresignedImage;
import com.learn.mistakeservice.service.MistakeImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Clock;
import java.time.Instant;

/** Garage 的 S3 存储适配器。 */
@Component
@RequiredArgsConstructor
public class S3MistakeImageStorage implements MistakeImageStorageService {

    private final S3Client mistakeS3Client;
    private final S3Presigner mistakeS3Presigner;
    private final MistakeStorageProperties properties;
    private final Clock clock = Clock.systemUTC();
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    @Override
    public void put(String objectKey, String contentType, byte[] content) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength((long) content.length)
                    .build();
            mistakeS3Client.putObject(request, RequestBody.fromBytes(content));
        } catch (RuntimeException exception) {
            throw new MistakeStorageException("错题图片上传失败", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            mistakeS3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build());
        } catch (RuntimeException exception) {
            throw new MistakeStorageException("错题图片删除失败", exception);
        }
    }

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

    @Override
    public byte[] get(String objectKey) {
        try {
            if (objectKey == null || objectKey.isBlank()) {
                throw new IllegalArgumentException("objectKey不能为空");
            }

            // 获取图片信息
            HeadObjectResponse headResponse = mistakeS3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(objectKey)
                            .build()
            );

            if (headResponse.contentLength() > MAX_FILE_SIZE) {
                throw new MistakeStorageException("错题图片大小不能超过10MB");
            }

            byte[] bytes = mistakeS3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(objectKey)
                            .build()
            ).asByteArray();

            // 如果图片过大则抛异常
            if (bytes.length > MAX_FILE_SIZE) {
                throw new MistakeStorageException("错题图片大小不能超过10MB");
            }

            return bytes;
        } catch (RuntimeException exception) {
            throw new MistakeStorageException("错题图片读取失败", exception);
        }
    }
}
