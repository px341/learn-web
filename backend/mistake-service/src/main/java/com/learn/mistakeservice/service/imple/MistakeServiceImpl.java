package com.learn.mistakeservice.service.imple;

import com.learn.mistakeservice.entity.PersonalQuestionEntity;
import com.learn.mistakeservice.exception.MistakeNotFoundException;
import com.learn.mistakeservice.mapper.MistakeMapper;
import com.learn.mistakeservice.model.AnalysisStatus;
import com.learn.mistakeservice.model.PresignedImage;
import com.learn.mistakeservice.service.MistakeImageStorageService;
import com.learn.mistakeservice.service.MistakeService;
import com.learn.mistakeservice.vo.MistakeAnalysisVO;
import com.learn.mistakeservice.vo.MistakeDetailVO;
import com.learn.mistakeservice.vo.MistakeImageVO;
import com.learn.security.currentuser.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MistakeServiceImpl implements MistakeService {

    private final MistakeMapper mistakeMapper;
    private final CurrentUserProvider currentUserProvider;
    private final MistakeImageStorageService imageStorageService;

    @Override
    public MistakeDetailVO getMistake(UUID id) {
        UUID userId = currentUserProvider.getUserId();
        PersonalQuestionEntity mistake = mistakeMapper
                .selectActiveByIdAndUserId(id, userId);
        if (mistake == null) {
            throw new MistakeNotFoundException();
        }

        return new MistakeDetailVO(
                mistake.getId(),
                mistake.getTitle(),
                mistake.getSubject(),
                mistake.getChapter(),
                mistake.getQuestionType(),
                mistake.getStemText(),
                mistake.getUserAnswer(),
                mistake.getAnalysisStatus(),
                mistake.isMastered(),
                mistake.getCreatedAt(),
                toImage(mistake.getImageObjectKey()),
                toAnalysis(mistake),
                mistake.getFailureMessage()
        );
    }

    private MistakeImageVO toImage(String objectKey) {
        if (objectKey == null) {
            return null;
        }
        PresignedImage image = imageStorageService.createReadUrl(objectKey);
        return new MistakeImageVO(image.url(), image.expiresAt());
    }

    private MistakeAnalysisVO toAnalysis(PersonalQuestionEntity mistake) {
        if (mistake.getAnalysisStatus() != AnalysisStatus.COMPLETED) {
            return null;
        }
        return new MistakeAnalysisVO(
                mistake.getAnalysisSummary(),
                mistake.getAnalysisKnowledge(),
                mistake.getAnalysisSteps(),
                mistake.getAnalysisSuggestion(),
                mistake.getAnalysisAnswer(),
                mistake.getAnalysisConfidence()
        );
    }
}
