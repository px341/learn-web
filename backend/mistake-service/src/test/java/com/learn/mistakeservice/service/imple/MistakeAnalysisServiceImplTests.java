package com.learn.mistakeservice.service.imple;

import com.learn.mistakeservice.entity.PersonalQuestionEntity;
import com.learn.mistakeservice.exception.AnalysisPermanentException;
import com.learn.mistakeservice.exception.MistakeStorageException;
import com.learn.mistakeservice.mapper.MistakeMapper;
import com.learn.mistakeservice.service.MistakeImageStorageService;
import com.learn.mistakeservice.service.QuestionAnalysisClient;
import com.learn.mistakeservice.vo.MistakeAnalysisVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MistakeAnalysisServiceImplTests {

    private static final UUID MISTAKE_ID = UUID.fromString(
            "13f22ae2-3a47-47d9-bcc7-8590f873c80f"
    );
    private static final int CLAIMED_VERSION = 7;

    @Mock
    private MistakeMapper mistakeMapper;
    @Mock
    private MistakeImageStorageService imageStorageService;
    @Mock
    private QuestionAnalysisClient questionAnalysisClient;

    private MistakeAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MistakeAnalysisServiceImpl(
                mistakeMapper, imageStorageService, questionAnalysisClient
        );
    }

    @Test
    void rejectsNullIdBeforeCallingDependencies() {
        assertThatThrownBy(() -> service.analyze(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mistakeId 不能为空");

        verifyNoInteractions(mistakeMapper, imageStorageService, questionAnalysisClient);
    }

    @Test
    void returnsIdempotentlyWhenTaskCannotBeClaimed() {
        when(mistakeMapper.claimAnalysis(MISTAKE_ID)).thenReturn(0);

        service.analyze(MISTAKE_ID);

        verify(mistakeMapper).claimAnalysis(MISTAKE_ID);
        verifyNoMoreInteractions(mistakeMapper);
        verifyNoInteractions(imageStorageService, questionAnalysisClient);
    }

    @Test
    void completesTextOnlyAnalysisWithClaimedVersion() {
        PersonalQuestionEntity question = claimedQuestion(null);
        MistakeAnalysisVO result = validResult();
        when(mistakeMapper.claimAnalysis(MISTAKE_ID)).thenReturn(1);
        when(mistakeMapper.selectByIdForAnalysis(MISTAKE_ID)).thenReturn(question);
        when(questionAnalysisClient.analyze(question, null)).thenReturn(result);
        when(mistakeMapper.markAnalysisCompleted(MISTAKE_ID, CLAIMED_VERSION, result))
                .thenReturn(1);

        service.analyze(MISTAKE_ID);

        verifyNoInteractions(imageStorageService);
        verify(mistakeMapper).markAnalysisCompleted(
                MISTAKE_ID, CLAIMED_VERSION, result
        );
        verify(mistakeMapper, never()).markAnalysisFailed(any(), anyInt(), any());
        verify(mistakeMapper, never()).releaseAnalysisForRetry(any(), anyInt());
    }

    @Test
    void readsGarageImageBeforeCallingAnalysisClient() {
        String objectKey = "users/u/questions/q/original.png";
        byte[] image = {1, 2, 3};
        PersonalQuestionEntity question = claimedQuestion(objectKey);
        MistakeAnalysisVO result = validResult();
        when(mistakeMapper.claimAnalysis(MISTAKE_ID)).thenReturn(1);
        when(mistakeMapper.selectByIdForAnalysis(MISTAKE_ID)).thenReturn(question);
        when(imageStorageService.get(objectKey)).thenReturn(image);
        when(questionAnalysisClient.analyze(question, image)).thenReturn(result);

        service.analyze(MISTAKE_ID);

        verify(imageStorageService).get(objectKey);
        verify(questionAnalysisClient).analyze(question, image);
    }

    @Test
    void marksPermanentFailureWithoutRequestingRetry() {
        PersonalQuestionEntity question = claimedQuestion(null);
        when(mistakeMapper.claimAnalysis(MISTAKE_ID)).thenReturn(1);
        when(mistakeMapper.selectByIdForAnalysis(MISTAKE_ID)).thenReturn(question);
        when(questionAnalysisClient.analyze(question, null))
                .thenThrow(new AnalysisPermanentException("无法识别题目内容"));

        service.analyze(MISTAKE_ID);

        verify(mistakeMapper).markAnalysisFailed(
                MISTAKE_ID, CLAIMED_VERSION, "无法识别题目内容"
        );
        verify(mistakeMapper, never()).releaseAnalysisForRetry(any(), anyInt());
    }

    @Test
    void releasesTemporaryFailureAndRethrowsForMessageRetry() {
        PersonalQuestionEntity question = claimedQuestion("image.png");
        MistakeStorageException failure = new MistakeStorageException("Garage 暂时不可用");
        when(mistakeMapper.claimAnalysis(MISTAKE_ID)).thenReturn(1);
        when(mistakeMapper.selectByIdForAnalysis(MISTAKE_ID)).thenReturn(question);
        when(imageStorageService.get("image.png")).thenThrow(failure);
        when(mistakeMapper.releaseAnalysisForRetry(MISTAKE_ID, CLAIMED_VERSION))
                .thenReturn(1);

        assertThatThrownBy(() -> service.analyze(MISTAKE_ID)).isSameAs(failure);

        verify(mistakeMapper).releaseAnalysisForRetry(MISTAKE_ID, CLAIMED_VERSION);
        verify(mistakeMapper, never()).markAnalysisFailed(any(), anyInt(), any());
        verifyNoInteractions(questionAnalysisClient);
    }

    @Test
    void treatsIncompleteModelResponseAsPermanentFailure() {
        PersonalQuestionEntity question = claimedQuestion(null);
        MistakeAnalysisVO incomplete = new MistakeAnalysisVO(
                null, List.of(), List.of(), "建议", "答案", 50
        );
        when(mistakeMapper.claimAnalysis(MISTAKE_ID)).thenReturn(1);
        when(mistakeMapper.selectByIdForAnalysis(MISTAKE_ID)).thenReturn(question);
        when(questionAnalysisClient.analyze(question, null)).thenReturn(incomplete);

        service.analyze(MISTAKE_ID);

        verify(mistakeMapper).markAnalysisFailed(
                MISTAKE_ID, CLAIMED_VERSION, "分析服务返回了不完整的结果"
        );
        verify(mistakeMapper, never()).markAnalysisCompleted(any(), anyInt(), any());
    }

    private PersonalQuestionEntity claimedQuestion(String imageObjectKey) {
        PersonalQuestionEntity question = new PersonalQuestionEntity();
        question.setId(MISTAKE_ID);
        question.setVersion(CLAIMED_VERSION);
        question.setImageObjectKey(imageObjectKey);
        return question;
    }

    private MistakeAnalysisVO validResult() {
        return new MistakeAnalysisVO(
                "错误原因",
                List.of("一元二次方程"),
                List.of("移项", "求解"),
                "复习配方法",
                "x = 2",
                92
        );
    }
}
