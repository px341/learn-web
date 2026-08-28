package com.learn.mistakeservice.entity;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/** 待发布的错题分析事件内部持久化对象。 */
@Data
public class MistakeOutboxEventEntity {
    private UUID id;
    private UUID mistakeId;
    private String eventType;
    private String status;
    private int attempts;
    private Instant nextAttemptAt;
    private String lastError;
    private Instant publishedAt;
    private Instant createdAt;
}
