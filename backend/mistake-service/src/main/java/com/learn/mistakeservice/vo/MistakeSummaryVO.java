package com.learn.mistakeservice.vo;

import com.learn.mistakeservice.model.AnalysisStatus;

import java.time.Instant;
import java.util.UUID;

/** 错题列表和创建接口复用的安全摘要视图。 */
public record MistakeSummaryVO(
        UUID id,
        String title,
        String subject,
        String chapter,
        String type,
        AnalysisStatus status,
        boolean mastered,
        Instant createdAt
) {
}
