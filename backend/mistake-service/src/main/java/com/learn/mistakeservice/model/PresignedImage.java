package com.learn.mistakeservice.model;

import java.time.Instant;

/** Service 内部的预签名结果，不属于数据库实体。 */
public record PresignedImage(
        String url,
        Instant expiresAt
) {
}
