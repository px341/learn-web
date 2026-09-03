package com.learn.mistakeservice.messaging;

import com.learn.mistakeservice.entity.PersonalQuestionEntity;
import com.learn.mistakeservice.mapper.MistakeMapper;
import com.learn.mistakeservice.mapper.MistakeOutboxMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AnalysisLeaseRecoveryTests {

    @Test
    void recoveredTaskGetsANewOutboxEvent() {
        MistakeMapper mistakeMapper = mock(MistakeMapper.class);
        MistakeOutboxMapper outboxMapper = mock(MistakeOutboxMapper.class);
        UUID mistakeId = UUID.fromString("47444c72-d94e-4537-a648-dcae20eb0b25");
        PersonalQuestionEntity question = new PersonalQuestionEntity();
        question.setId(mistakeId);
        question.setVersion(11);
        when(mistakeMapper.selectExpiredAnalysesForUpdate(any(), eq(50)))
                .thenReturn(List.of(question));
        when(mistakeMapper.recoverExpiredAnalysis(mistakeId, 11)).thenReturn(1);

        new AnalysisLeaseRecovery(mistakeMapper, outboxMapper, Duration.ofMinutes(10))
                .recoverExpired();

        verify(outboxMapper).insertAnalysisRequested(any(UUID.class), eq(mistakeId), any());
    }

    @Test
    void staleTaskDoesNotGetAnOutboxEvent() {
        MistakeMapper mistakeMapper = mock(MistakeMapper.class);
        MistakeOutboxMapper outboxMapper = mock(MistakeOutboxMapper.class);
        PersonalQuestionEntity question = new PersonalQuestionEntity();
        question.setId(UUID.randomUUID());
        question.setVersion(3);
        when(mistakeMapper.selectExpiredAnalysesForUpdate(any(), eq(50)))
                .thenReturn(List.of(question));
        when(mistakeMapper.recoverExpiredAnalysis(question.getId(), 3)).thenReturn(0);

        new AnalysisLeaseRecovery(mistakeMapper, outboxMapper, Duration.ofMinutes(10))
                .recoverExpired();

        verifyNoInteractions(outboxMapper);
    }
}
