package com.learn.mistakeservice.service.imple;

import com.learn.mistakeservice.entity.PersonalQuestionEntity;
import com.learn.mistakeservice.exception.MistakeNotFoundException;
import com.learn.mistakeservice.mapper.MistakeMapper;
import com.learn.mistakeservice.model.AnalysisStatus;
import com.learn.mistakeservice.model.PresignedImage;
import com.learn.mistakeservice.vo.MistakeDetailVO;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MistakeServiceImplTests {

    @Test
    void scopesLookupToCurrentUserAndUsesUniformNotFoundError() {
        UUID mistakeId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        AtomicReference<UUID> queriedUserId = new AtomicReference<>();

        MistakeMapper mapper = (id, userId) -> {
            queriedUserId.set(userId);
            return null;
        };
        MistakeServiceImpl service = new MistakeServiceImpl(
                mapper,
                () -> currentUserId,
                objectKey -> new PresignedImage("unused", Instant.EPOCH)
        );

        assertThatThrownBy(() -> service.getMistake(mistakeId))
                .isInstanceOf(MistakeNotFoundException.class)
                .hasMessage("错题不存在");
        assertThat(queriedUserId.get()).isEqualTo(currentUserId);
    }

    @Test
    void mapsCompletedAnalysisAndCreatesTemporaryImageUrl() {
        UUID mistakeId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-22T01:42:00Z");
        Instant expiresAt = Instant.parse("2026-08-22T02:02:00Z");
        PersonalQuestionEntity entity = completedMistake(mistakeId, createdAt);

        MistakeServiceImpl service = new MistakeServiceImpl(
                (id, userId) -> entity,
                UUID::randomUUID,
                objectKey -> {
                    assertThat(objectKey).isEqualTo("users/u/mistakes/m/original.png");
                    return new PresignedImage("http://localhost:3900/signed", expiresAt);
                }
        );

        MistakeDetailVO result = service.getMistake(mistakeId);

        assertThat(result.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(result.image().url()).isEqualTo("http://localhost:3900/signed");
        assertThat(result.image().expiresAt()).isEqualTo(expiresAt);
        assertThat(result.analysis().confidence()).isEqualTo(94);
        assertThat(result.analysis().knowledge()).containsExactly("顶点式", "开口方向");
        assertThat(result.failureMessage()).isNull();
    }

    private PersonalQuestionEntity completedMistake(UUID id, Instant createdAt) {
        PersonalQuestionEntity entity = new PersonalQuestionEntity();
        entity.setId(id);
        entity.setTitle("二次函数图像与最值");
        entity.setSubject("数学");
        entity.setChapter("函数");
        entity.setQuestionType("概念不清");
        entity.setStemText("已知二次函数……");
        entity.setUserAnswer("x = 2");
        entity.setAnalysisStatus(AnalysisStatus.COMPLETED);
        entity.setMastered(false);
        entity.setCreatedAt(createdAt);
        entity.setImageObjectKey("users/u/mistakes/m/original.png");
        entity.setAnalysisSummary("混淆了开口方向与顶点坐标的关系");
        entity.setAnalysisKnowledge(List.of("顶点式", "开口方向"));
        entity.setAnalysisSteps(List.of("化为顶点式", "判断开口方向"));
        entity.setAnalysisSuggestion("重新练习顶点式变形题");
        entity.setAnalysisAnswer("当 a > 0 时有最小值");
        entity.setAnalysisConfidence(94);
        return entity;
    }
}
