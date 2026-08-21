package com.learn.auth.entity;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * users 表的内部持久化对象。
 *
 * <p>包含 passwordHash 等认证字段，只能在 Auth Service 内部使用，不能直接序列化返回前端。</p>
 */
@Data
public class UserEntity {
    private UUID id;
    private String name;
    private String email;
    private String passwordHash;
    private Integer credits;
    private String status;
    private Integer tokenVersion;
    private Instant emailVerifiedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
