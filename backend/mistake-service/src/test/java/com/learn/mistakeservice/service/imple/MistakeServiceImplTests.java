package com.learn.mistakeservice.service.imple;

import com.learn.mistakeservice.dto.CreateMistakeDTO;
import com.learn.mistakeservice.entity.MistakeOutboxEventEntity;
import com.learn.mistakeservice.entity.PersonalQuestionEntity;
import com.learn.mistakeservice.exception.MistakeNotFoundException;
import com.learn.mistakeservice.mapper.MistakeMapper;
import com.learn.mistakeservice.mapper.MistakeOutboxMapper;
import com.learn.mistakeservice.model.AnalysisStatus;
import com.learn.mistakeservice.model.PresignedImage;
import com.learn.mistakeservice.service.MistakeImageStorageService;
import com.learn.mistakeservice.support.MistakeImageValidator;
import com.learn.mistakeservice.vo.CreateMistakeVO;
import com.learn.mistakeservice.vo.MistakeDetailVO;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MistakeServiceImplTests {

    @Test
    void scopesLookupToCurrentUserAndUsesUniformNotFoundError() {
        UUID mistakeId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        StubMistakeMapper mapper = new StubMistakeMapper();
        MistakeServiceImpl service = new MistakeServiceImpl(
                mapper, noopOutbox(),
                () -> currentUserId,
                new MistakeImageValidator(),
                new NoopStorage()
        );

        assertThatThrownBy(() -> service.getMistake(mistakeId))
                .isInstanceOf(MistakeNotFoundException.class)
                .hasMessage("错题不存在");
        assertThat(mapper.queriedUserId).isEqualTo(currentUserId);
    }

    @Test
    void mapsCompletedAnalysisAndCreatesTemporaryImageUrl() {
        UUID mistakeId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-22T01:42:00Z");
        Instant expiresAt = Instant.parse("2026-08-22T02:02:00Z");
        PersonalQuestionEntity entity = completedMistake(mistakeId, createdAt);

        StubMistakeMapper mapper = new StubMistakeMapper();
        mapper.selected = entity;
        MistakeImageStorageService storage = new NoopStorage() {
            @Override
            public PresignedImage createReadUrl(String objectKey) {
                assertThat(objectKey).isEqualTo("users/u/mistakes/m/original.png");
                return new PresignedImage("http://localhost:3900/signed", expiresAt);
            }
        };
        MistakeServiceImpl service = new MistakeServiceImpl(
                mapper, noopOutbox(), UUID::randomUUID,
                new MistakeImageValidator(), storage
        );

        MistakeDetailVO result = service.getMistake(mistakeId);

        assertThat(result.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(result.image().url()).isEqualTo("http://localhost:3900/signed");
        assertThat(result.image().expiresAt()).isEqualTo(expiresAt);
        assertThat(result.analysis().confidence()).isEqualTo(94);
        assertThat(result.analysis().knowledge()).containsExactly("顶点式", "开口方向");
        assertThat(result.failureMessage()).isNull();
    }

    @Test
    void createsQueuedMistakeAndDeductsOneCredit() {
        UUID currentUserId = UUID.randomUUID();
        StubMistakeMapper mapper = new StubMistakeMapper();
        mapper.credits = 3;
        UUID[] outboxMistakeId = new UUID[1];
        MistakeOutboxMapper outbox = new NoopOutboxMapper() {
            @Override
            public int insertAnalysisRequested(UUID id, UUID mistakeId, Instant createdAt) {
                outboxMistakeId[0] = mistakeId;
                return 1;
            }
        };
        MistakeServiceImpl service = new MistakeServiceImpl(
                mapper, outbox, () -> currentUserId,
                new MistakeImageValidator(), new NoopStorage()
        );

        CreateMistakeVO result = service.createMistake(
                new CreateMistakeDTO(null, "数学", "函数", "概念不清", " 已知二次函数…… ", "x = 2"),
                null
        );

        assertThat(result.creditsRemaining()).isEqualTo(2);
        assertThat(result.mistake().status()).isEqualTo(AnalysisStatus.QUEUED);
        assertThat(mapper.inserted.getUserId()).isEqualTo(currentUserId);
        assertThat(mapper.inserted.getTitle()).isEqualTo("已知二次函数……");
        assertThat(outboxMistakeId[0]).isEqualTo(mapper.inserted.getId());
    }

    private MistakeOutboxMapper noopOutbox() {
        return new NoopOutboxMapper();
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

    private static class StubMistakeMapper implements MistakeMapper {
        private PersonalQuestionEntity selected;
        private PersonalQuestionEntity inserted;
        private UUID queriedUserId;
        private Integer credits;

        @Override
        public PersonalQuestionEntity selectActiveByIdAndUserId(UUID id, UUID userId) {
            queriedUserId = userId;
            return selected;
        }

        @Override
        public int insert(PersonalQuestionEntity mistake) {
            inserted = mistake;
            return 1;
        }

        @Override
        public Integer selectActiveCreditsForUpdate(UUID userId) {
            return credits;
        }

        @Override
        public int decrementCredit(UUID userId) {
            credits--;
            return 1;
        }
    }

    private static class NoopOutboxMapper implements MistakeOutboxMapper {
        @Override
        public int insertAnalysisRequested(UUID id, UUID mistakeId, Instant createdAt) {
            return 1;
        }

        @Override
        public List<MistakeOutboxEventEntity> selectPendingForUpdate(int limit) {
            return List.of();
        }

        @Override
        public int markPublished(UUID id, Instant publishedAt) {
            return 1;
        }

        @Override
        public int markRetry(UUID id, String lastError) {
            return 1;
        }
    }

    private static class NoopStorage implements MistakeImageStorageService {
        @Override
        public void put(String objectKey, String contentType, byte[] content) {
        }

        @Override
        public void delete(String objectKey) {
        }

        @Override
        public PresignedImage createReadUrl(String objectKey) {
            return new PresignedImage("unused", Instant.EPOCH);
        }
    }
}
