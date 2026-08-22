package com.learn.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.Instant;
import java.util.UUID;

/**
 * 业务实体可按需继承的通用主键和审计时间基类。
 *
 * <p>该类使用 JPA 生命周期回调维护时间字段，适用于由 JPA 持久化的实体。
 * 使用 MyBatis 的模块不会自动触发这些回调，需要在 SQL 或业务代码中维护审计时间。</p>
 */
@MappedSuperclass
public abstract class BaseEntity {

    /** 由 JPA Provider 生成的 UUID 主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 首次持久化时间，创建后不允许通过 JPA 更新。 */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** 最近一次由 JPA 更新实体的时间。 */
    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * 首次持久化前使用同一时刻初始化创建时间和更新时间。
     */
    @PrePersist
    protected void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * 已存在实体更新前刷新更新时间。
     */
    @PreUpdate
    protected void preUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * @return 实体 UUID；首次持久化前可能为 {@code null}
     */
    public UUID getId() {
        return id;
    }

    /**
     * @return 创建时间；首次持久化前可能为 {@code null}
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * @return 最近更新时间；首次持久化前可能为 {@code null}
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
