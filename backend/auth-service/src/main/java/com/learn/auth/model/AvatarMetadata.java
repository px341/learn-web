package com.learn.auth.model;

import java.time.Instant;

/** users 表中保存的头像对象元数据，不包含二进制和临时访问 URL。 */
public record AvatarMetadata(
        String bucket,
        String objectKey,
        String originalName,
        String contentType,
        long size,
        String sha256,
        Instant updatedAt
) {
}
