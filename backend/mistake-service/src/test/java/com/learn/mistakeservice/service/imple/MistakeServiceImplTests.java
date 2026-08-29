package com.learn.mistakeservice.service.imple;

import com.learn.mistakeservice.dto.CreateMistakeDTO;
import com.learn.mistakeservice.dto.UpdateMasteryDTO;
import com.learn.mistakeservice.entity.PersonalQuestionEntity;
import com.learn.mistakeservice.exception.InsufficientCreditsException;
import com.learn.mistakeservice.exception.MistakeContentRequiredException;
import com.learn.mistakeservice.exception.MistakeNotFoundException;
import com.learn.mistakeservice.exception.MistakeUserUnavailableException;
import com.learn.mistakeservice.mapper.MistakeMapper;
import com.learn.mistakeservice.mapper.MistakeOutboxMapper;
import com.learn.mistakeservice.model.AnalysisStatus;
import com.learn.mistakeservice.model.PresignedImage;
import com.learn.mistakeservice.service.MistakeImageStorageService;
import com.learn.mistakeservice.support.MistakeImageValidator;
import com.learn.mistakeservice.vo.CreateMistakeVO;
import com.learn.mistakeservice.vo.MistakeDetailVO;
import com.learn.mistakeservice.vo.MistakeSummaryVO;
import com.learn.security.currentuser.CurrentUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MistakeServiceImplTests {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MISTAKE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant CREATED_AT = Instant.parse("2026-08-22T01:42:00Z");

    @Mock
    private MistakeMapper mistakeMapper;

    @Mock
    private MistakeOutboxMapper outboxMapper;

    @Mock
    private MistakeImageStorageService imageStorageService;

    private MistakeServiceImpl service;

    @BeforeEach
    void setUp() {
        CurrentUserProvider currentUserProvider = () -> USER_ID;
        service = new MistakeServiceImpl(
                mistakeMapper,
                outboxMapper,
                currentUserProvider,
                new MistakeImageValidator(),
                imageStorageService
        );
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Nested
    class GetMistake {

        @Test
        void scopesLookupToCurrentUserAndUsesUniformNotFoundError() {
            assertThatThrownBy(() -> service.getMistake(MISTAKE_ID))
                    .isInstanceOf(MistakeNotFoundException.class)
                    .hasMessage("错题不存在");

            verify(mistakeMapper).selectActiveByIdAndUserId(MISTAKE_ID, USER_ID);
            verifyNoInteractions(imageStorageService);
        }

        @Test
        void mapsCompletedAnalysisAndCreatesTemporaryImageUrl() {
            PersonalQuestionEntity mistake = completedMistake();
            Instant expiresAt = Instant.parse("2026-08-22T02:02:00Z");
            when(mistakeMapper.selectActiveByIdAndUserId(MISTAKE_ID, USER_ID))
                    .thenReturn(mistake);
            when(imageStorageService.createReadUrl(mistake.getImageObjectKey()))
                    .thenReturn(new PresignedImage("http://localhost:3900/signed", expiresAt));

            MistakeDetailVO result = service.getMistake(MISTAKE_ID);

            assertThat(result)
                    .extracting(
                            MistakeDetailVO::id,
                            MistakeDetailVO::status,
                            MistakeDetailVO::mastered,
                            MistakeDetailVO::failureMessage
                    )
                    .containsExactly(MISTAKE_ID, AnalysisStatus.COMPLETED, false, null);
            assertThat(result.image().url()).isEqualTo("http://localhost:3900/signed");
            assertThat(result.image().expiresAt()).isEqualTo(expiresAt);
            assertThat(result.analysis().confidence()).isEqualTo(94);
            assertThat(result.analysis().knowledge()).containsExactly("顶点式", "开口方向");
        }

        @Test
        void omitsImageAndAnalysisWhileAnalysisIsQueued() {
            PersonalQuestionEntity mistake = baseMistake();
            mistake.setAnalysisStatus(AnalysisStatus.QUEUED);
            when(mistakeMapper.selectActiveByIdAndUserId(MISTAKE_ID, USER_ID))
                    .thenReturn(mistake);

            MistakeDetailVO result = service.getMistake(MISTAKE_ID);

            assertThat(result.image()).isNull();
            assertThat(result.analysis()).isNull();
            verifyNoInteractions(imageStorageService);
        }
    }

    @Nested
    class CreateMistake {

        @Test
        void rejectsRequestWithoutTextOrImageBeforeUsingTheCurrentUser() {
            assertThatThrownBy(() -> service.createMistake(
                    request(null, "  ", "  "),
                    null
            )).isInstanceOf(MistakeContentRequiredException.class);

            verifyNoInteractions(mistakeMapper, outboxMapper, imageStorageService);
        }

        @Test
        void createsQueuedMistakeAndDeductsOneCreditInOrder() {
            when(mistakeMapper.selectActiveCreditsForUpdate(USER_ID)).thenReturn(3);
            when(mistakeMapper.decrementCredit(USER_ID)).thenReturn(1);
            when(mistakeMapper.insert(any())).thenReturn(1);
            when(outboxMapper.insertAnalysisRequested(any(), any(), any())).thenReturn(1);

            CreateMistakeVO result = service.createMistake(
                    request(null, " 已知二次函数…… ", " x = 2 "),
                    null
            );

            ArgumentCaptor<PersonalQuestionEntity> mistakeCaptor =
                    ArgumentCaptor.forClass(PersonalQuestionEntity.class);
            verify(mistakeMapper).insert(mistakeCaptor.capture());
            PersonalQuestionEntity inserted = mistakeCaptor.getValue();
            assertThat(inserted.getUserId()).isEqualTo(USER_ID);
            assertThat(inserted.getTitle()).isEqualTo("已知二次函数……");
            assertThat(inserted.getStemText()).isEqualTo("已知二次函数……");
            assertThat(inserted.getUserAnswer()).isEqualTo("x = 2");
            assertThat(inserted.getAnalysisStatus()).isEqualTo(AnalysisStatus.QUEUED);
            assertThat(inserted.isMastered()).isFalse();
            assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
            assertThat(inserted.getVersion()).isZero();
            assertThat(inserted.getCreatedAt()).isEqualTo(inserted.getUpdatedAt());

            assertThat(result.creditsRemaining()).isEqualTo(2);
            assertThat(result.mistake().id()).isEqualTo(inserted.getId());
            verify(outboxMapper).insertAnalysisRequested(any(), eq(inserted.getId()), eq(inserted.getCreatedAt()));

            InOrder writeOrder = inOrder(mistakeMapper, outboxMapper);
            writeOrder.verify(mistakeMapper).selectActiveCreditsForUpdate(USER_ID);
            writeOrder.verify(mistakeMapper).decrementCredit(USER_ID);
            writeOrder.verify(mistakeMapper).insert(inserted);
            writeOrder.verify(outboxMapper)
                    .insertAnalysisRequested(any(), eq(inserted.getId()), eq(inserted.getCreatedAt()));
        }

        @Test
        void storesValidatedImageMetadataAndKeepsImageAfterCommit() throws Exception {
            beginTransactionSynchronization();
            when(mistakeMapper.selectActiveCreditsForUpdate(USER_ID)).thenReturn(2);
            when(mistakeMapper.decrementCredit(USER_ID)).thenReturn(1);
            when(mistakeMapper.insert(any())).thenReturn(1);
            when(outboxMapper.insertAnalysisRequested(any(), any(), any())).thenReturn(1);
            MockMultipartFile image = pngImage();

            service.createMistake(request("图片题", null, null), image);

            ArgumentCaptor<PersonalQuestionEntity> mistakeCaptor =
                    ArgumentCaptor.forClass(PersonalQuestionEntity.class);
            verify(mistakeMapper).insert(mistakeCaptor.capture());
            PersonalQuestionEntity inserted = mistakeCaptor.getValue();
            assertThat(inserted.getImageObjectKey())
                    .startsWith("users/" + USER_ID + "/mistakes/")
                    .endsWith("/original.png");
            assertThat(inserted.getImageOriginalName()).isEqualTo("question.png");
            assertThat(inserted.getImageContentType()).isEqualTo("image/png");
            assertThat(inserted.getImageSize()).isEqualTo(image.getSize());
            assertThat(inserted.getImageSha256()).matches("[0-9a-f]{64}");
            verify(imageStorageService).put(
                    inserted.getImageObjectKey(),
                    "image/png",
                    image.getBytes()
            );

            completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

            verify(imageStorageService, never()).delete(any());
        }

        @Test
        void deletesUploadedImageWhenDatabaseWorkRollsBack() {
            beginTransactionSynchronization();
            when(mistakeMapper.selectActiveCreditsForUpdate(USER_ID)).thenReturn(2);
            when(mistakeMapper.decrementCredit(USER_ID)).thenReturn(1);
            when(mistakeMapper.insert(any())).thenReturn(0);

            assertThatThrownBy(() -> service.createMistake(
                    request("图片题", null, null),
                    pngImage()
            )).isInstanceOf(IllegalStateException.class)
                    .hasMessage("创建错题失败");

            ArgumentCaptor<String> objectKey = ArgumentCaptor.forClass(String.class);
            verify(imageStorageService).put(objectKey.capture(), eq("image/png"), any());
            completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
            verify(imageStorageService).delete(objectKey.getValue());
            verifyNoInteractions(outboxMapper);
        }

        @Test
        void rejectsImageCreationOutsideTransactionAndCleansUpUpload() {
            assertThatThrownBy(() -> service.createMistake(
                    request("图片题", null, null),
                    pngImage()
            )).isInstanceOf(IllegalStateException.class)
                    .hasMessage("创建错题必须在事务中执行");

            ArgumentCaptor<String> objectKey = ArgumentCaptor.forClass(String.class);
            verify(imageStorageService).put(objectKey.capture(), eq("image/png"), any());
            verify(imageStorageService).delete(objectKey.getValue());
            verify(mistakeMapper, never()).selectActiveCreditsForUpdate(any());
        }

        @Test
        void rejectsMissingActiveUser() {
            when(mistakeMapper.selectActiveCreditsForUpdate(USER_ID)).thenReturn(null);

            assertThatThrownBy(() -> service.createMistake(
                    request("标题", "题目", null),
                    null
            )).isInstanceOf(MistakeUserUnavailableException.class);

            verify(mistakeMapper, never()).decrementCredit(any());
            verify(mistakeMapper, never()).insert(any());
            verifyNoInteractions(outboxMapper);
        }

        @Test
        void rejectsExhaustedCredits() {
            when(mistakeMapper.selectActiveCreditsForUpdate(USER_ID)).thenReturn(0);

            assertThatThrownBy(() -> service.createMistake(
                    request("标题", "题目", null),
                    null
            )).isInstanceOf(InsufficientCreditsException.class);

            verify(mistakeMapper, never()).decrementCredit(any());
            verify(mistakeMapper, never()).insert(any());
            verifyNoInteractions(outboxMapper);
        }

        @Test
        void failsWhenCreditUpdateDoesNotAffectExactlyOneRow() {
            when(mistakeMapper.selectActiveCreditsForUpdate(USER_ID)).thenReturn(2);
            when(mistakeMapper.decrementCredit(USER_ID)).thenReturn(0);

            assertThatThrownBy(() -> service.createMistake(
                    request("标题", "题目", null),
                    null
            )).isInstanceOf(IllegalStateException.class)
                    .hasMessage("扣减分析额度失败");

            verify(mistakeMapper, never()).insert(any());
            verifyNoInteractions(outboxMapper);
        }

        @Test
        void failsWhenOutboxInsertDoesNotAffectExactlyOneRow() {
            when(mistakeMapper.selectActiveCreditsForUpdate(USER_ID)).thenReturn(2);
            when(mistakeMapper.decrementCredit(USER_ID)).thenReturn(1);
            when(mistakeMapper.insert(any())).thenReturn(1);
            when(outboxMapper.insertAnalysisRequested(any(), any(), any())).thenReturn(0);

            assertThatThrownBy(() -> service.createMistake(
                    request("标题", "题目", null),
                    null
            )).isInstanceOf(IllegalStateException.class)
                    .hasMessage("创建分析任务失败");
        }
    }

    @Nested
    class UpdateMastery {

        @Test
        void rejectsMistakeNotOwnedByCurrentUser() {
            assertThatThrownBy(() -> service.updateMastery(
                    MISTAKE_ID,
                    new UpdateMasteryDTO(true)
            )).isInstanceOf(MistakeNotFoundException.class);

            verify(mistakeMapper).selectActiveByIdAndUserId(MISTAKE_ID, USER_ID);
            verify(mistakeMapper, never()).updateMasteredByIdAndUserId(any(), any());
        }

        @ParameterizedTest
        @CsvSource({"false,true", "true,false"})
        void togglesMasteryWhenRequestedValueChanged(boolean current, boolean requested) {
            PersonalQuestionEntity mistake = baseMistake();
            mistake.setMastered(current);
            when(mistakeMapper.selectActiveByIdAndUserId(MISTAKE_ID, USER_ID))
                    .thenReturn(mistake);
            when(mistakeMapper.updateMasteredByIdAndUserId(MISTAKE_ID, USER_ID))
                    .thenReturn(1);

            MistakeSummaryVO result = service.updateMastery(
                    MISTAKE_ID,
                    new UpdateMasteryDTO(requested)
            );

            assertThat(result.mastered()).isEqualTo(requested);
            verify(mistakeMapper).updateMasteredByIdAndUserId(MISTAKE_ID, USER_ID);
        }

        @Test
        void skipsWriteWhenMasteryAlreadyMatches() {
            PersonalQuestionEntity mistake = baseMistake();
            mistake.setMastered(true);
            when(mistakeMapper.selectActiveByIdAndUserId(MISTAKE_ID, USER_ID))
                    .thenReturn(mistake);

            MistakeSummaryVO result = service.updateMastery(
                    MISTAKE_ID,
                    new UpdateMasteryDTO(true)
            );

            assertThat(result.mastered()).isTrue();
            verify(mistakeMapper, never()).updateMasteredByIdAndUserId(any(), any());
        }

        @Test
        void reportsNotFoundWhenConcurrentUpdateAffectsNoRows() {
            PersonalQuestionEntity mistake = baseMistake();
            when(mistakeMapper.selectActiveByIdAndUserId(MISTAKE_ID, USER_ID))
                    .thenReturn(mistake);
            when(mistakeMapper.updateMasteredByIdAndUserId(MISTAKE_ID, USER_ID))
                    .thenReturn(0);

            assertThatThrownBy(() -> service.updateMastery(
                    MISTAKE_ID,
                    new UpdateMasteryDTO(true)
            )).isInstanceOf(MistakeNotFoundException.class);
        }
    }

    private static CreateMistakeDTO request(String title, String text, String userAnswer) {
        return new CreateMistakeDTO(
                title,
                " 数学 ",
                " 函数 ",
                " 概念不清 ",
                text,
                userAnswer
        );
    }

    private static PersonalQuestionEntity baseMistake() {
        PersonalQuestionEntity mistake = new PersonalQuestionEntity();
        mistake.setId(MISTAKE_ID);
        mistake.setUserId(USER_ID);
        mistake.setTitle("二次函数图像与最值");
        mistake.setSubject("数学");
        mistake.setChapter("函数");
        mistake.setQuestionType("概念不清");
        mistake.setStemText("已知二次函数……");
        mistake.setUserAnswer("x = 2");
        mistake.setAnalysisStatus(AnalysisStatus.QUEUED);
        mistake.setCreatedAt(CREATED_AT);
        return mistake;
    }

    private static PersonalQuestionEntity completedMistake() {
        PersonalQuestionEntity mistake = baseMistake();
        mistake.setAnalysisStatus(AnalysisStatus.COMPLETED);
        mistake.setImageObjectKey("users/u/mistakes/m/original.png");
        mistake.setAnalysisSummary("混淆了开口方向与顶点坐标的关系");
        mistake.setAnalysisKnowledge(List.of("顶点式", "开口方向"));
        mistake.setAnalysisSteps(List.of("化为顶点式", "判断开口方向"));
        mistake.setAnalysisSuggestion("重新练习顶点式变形题");
        mistake.setAnalysisAnswer("当 a > 0 时有最小值");
        mistake.setAnalysisConfidence(94);
        return mistake;
    }

    private static MockMultipartFile pngImage() {
        return new MockMultipartFile(
                "image",
                "question.png",
                "text/plain",
                new byte[]{
                        (byte) 0x89, 0x50, 0x4E, 0x47,
                        0x0D, 0x0A, 0x1A, 0x0A
                }
        );
    }

    private static void beginTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    private static void completeTransaction(int status) {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(status));
    }
}
