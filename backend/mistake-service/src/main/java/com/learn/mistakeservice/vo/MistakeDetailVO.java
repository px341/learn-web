package com.learn.mistakeservice.vo;

import com.learn.mistakeservice.model.AnalysisStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** 错题详情视图，不包含用户 ID、对象键和存储凭据。 */
public record MistakeDetailVO(
        UUID id,
        String title,
        String subject,
        String chapter,
        String type,
        String questionText,
        String userAnswer,
        AnalysisStatus status,
        boolean mastered,
        Instant createdAt,
        @Schema(nullable = true) MistakeImageVO image,
        @Schema(nullable = true) MistakeAnalysisVO analysis,
        @Schema(nullable = true) String failureMessage
) {
}
