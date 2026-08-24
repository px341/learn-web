package com.learn.mistakeservice.entity;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code personal_questions} 表的内部持久化对象。
 *
 * <p>Dashboard 通过该实体对应的数据统计累计错题、本周新增和错因分布，但不会把实体
 * 直接返回给客户端。正确率和待复习统计应关联后续的分析/复习记录实体，而不是在
 * Dashboard 下建立重复的统计实体。</p>
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
