package com.learn.mistakeservice.entity;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code personal_questions} 表的内部持久化对象。
 *
 * <p>Dashboard 直接聚合该表中的累计错题、近 7 天新增和题型分布，
 * 但不会把该实体直接返回给客户端。</p>
 */
@Data
public class PersonalQuestionEntity {

    private UUID id;
    private UUID userId;
    private UUID matchedOfficialQuestionId;

    private String title;
    private String subject;
    private String chapter;
    private String questionType;
    private String stemText;

    private String imageObjectKey;
    private String imageOriginalName;
    private String imageContentType;
    private Long imageSize;
    private String imageSha256;

    private String status;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
}
