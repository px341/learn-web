package com.learn.mistakeservice.entity;

import com.learn.mistakeservice.model.AnalysisStatus;
import lombok.Data;

import java.time.Instant;
import java.util.List;
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
    private String userAnswer;

    private String imageObjectKey;
    private String imageOriginalName;
    private String imageContentType;
    private Long imageSize;
    private String imageSha256;

    /** 记录生命周期，只允许 ACTIVE/ARCHIVED；不直接作为接口 status 返回。 */
    private String status;

    private AnalysisStatus analysisStatus;
    private boolean mastered;

    private String analysisSummary;
    private List<String> analysisKnowledge;
    private List<String> analysisSteps;
    private String analysisSuggestion;
    private String analysisAnswer;
    private Integer analysisConfidence;
    private String failureMessage;

    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
}
