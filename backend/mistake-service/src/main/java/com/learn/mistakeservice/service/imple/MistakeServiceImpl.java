package com.learn.mistakeservice.service.imple;

import com.learn.common.vo.PageVO;
import com.learn.mistakeservice.dto.CreateMistakeDTO;
import com.learn.mistakeservice.dto.MistakeListQueryDTO;
import com.learn.mistakeservice.dto.UpdateMasteryDTO;
import com.learn.mistakeservice.entity.PersonalQuestionEntity;
import com.learn.mistakeservice.exception.InsufficientCreditsException;
import com.learn.mistakeservice.exception.AnalysisNotCompletedException;
import com.learn.mistakeservice.exception.MistakeContentRequiredException;
import com.learn.mistakeservice.exception.MistakeNotFoundException;
import com.learn.mistakeservice.exception.MistakeStorageException;
import com.learn.mistakeservice.exception.MistakeUserUnavailableException;
import com.learn.mistakeservice.mapper.MistakeMapper;
import com.learn.mistakeservice.mapper.MistakeOutboxMapper;
import com.learn.mistakeservice.model.AnalysisStatus;
import com.learn.mistakeservice.model.PresignedImage;
import com.learn.mistakeservice.model.ValidatedMistakeImage;
import com.learn.mistakeservice.service.MistakeImageStorageService;
import com.learn.mistakeservice.service.MistakeService;
import com.learn.mistakeservice.support.MistakeImageValidator;
import com.learn.mistakeservice.vo.CreateMistakeVO;
import com.learn.mistakeservice.vo.MistakeAnalysisVO;
import com.learn.mistakeservice.vo.MistakeDetailVO;
import com.learn.mistakeservice.vo.MistakeImageVO;
import com.learn.mistakeservice.vo.MistakeSummaryVO;
import com.learn.security.currentuser.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 错题详情查询与创建的统一业务服务。
 *
 * <p>创建错题时，错题记录、额度扣减和 Outbox 事件处于同一个数据库事务中；
 * 图片存储不支持数据库事务，因此通过事务完成回调在回滚时删除已上传图片。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MistakeServiceImpl implements MistakeService {

    private static final int MAX_TITLE_LENGTH = 100;

    private final MistakeMapper mistakeMapper;
    private final MistakeOutboxMapper outboxMapper;
    private final CurrentUserProvider currentUserProvider;
    private final MistakeImageValidator imageValidator;
    private final MistakeImageStorageService imageStorageService;
    private final Clock clock = Clock.systemUTC();

    @Override
    public PageVO<MistakeSummaryVO> listMistakes(MistakeListQueryDTO query) {
        UUID userId = currentUserProvider.getUserId();
        String keyword = normalizeNullable(query.getKeyword());
        String subject = normalizeNullable(query.getSubject());
        String analysisStatus = query.getStatus() == null
                ? null
                : query.getStatus().name();
        boolean ascending = query.getSort().toLowerCase().endsWith(",asc");
        long requestedOffset = (long) query.getPage() * query.getSize();
        int offset = requestedOffset > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) requestedOffset;

        List<MistakeSummaryVO> items = mistakeMapper.selectActiveByUserId(
                        userId,
                        keyword,
                        subject,
                        analysisStatus,
                        query.getMastered(),
                        offset,
                        query.getSize(),
                        ascending
                ).stream()
                .map(this::toSummary)
                .toList();
        long total = mistakeMapper.countActiveByUserId(
                userId, keyword, subject, analysisStatus, query.getMastered()
        );
        int totalPages = total == 0
                ? 0
                : (int) Math.ceil((double) total / query.getSize());
        return new PageVO<>(items, query.getPage(), query.getSize(), total, totalPages);
    }

    /**
     * 查询当前用户的错题详情。
     *
     * <p>Mapper 同时使用错题 ID 和当前用户 ID 查询，使“不存在”和“属于其他用户”
     * 统一表现为 {@link MistakeNotFoundException}，避免暴露其他用户的数据。</p>
     */
    @Override
    public MistakeDetailVO getMistake(UUID id) {
        UUID userId = currentUserProvider.getUserId();
        PersonalQuestionEntity mistake = mistakeMapper
                .selectActiveByIdAndUserId(id, userId);
        if (mistake == null) {
            throw new MistakeNotFoundException();
        }
        return new MistakeDetailVO(
                mistake.getId(), mistake.getTitle(), mistake.getSubject(),
                mistake.getChapter(), mistake.getQuestionType(), mistake.getStemText(),
                mistake.getUserAnswer(), mistake.getAnalysisStatus(), mistake.isMastered(),
                mistake.getCreatedAt(), toImage(mistake.getImageObjectKey()),
                toAnalysis(mistake), mistake.getFailureMessage()
        );
    }

    /**
     * 创建错题并写入待发布的分析事件。
     *
     * <p>主要流程：校验内容与图片、上传图片、锁定并扣减额度、保存错题、写入 Outbox。
     * 方法返回时仅表示请求已受理，真正的错题分析由异步消费者完成。</p>
     */
    @Override
    @Transactional
    public CreateMistakeVO createMistake(CreateMistakeDTO request, MultipartFile image) {
        String questionText = normalizeNullable(request.text());
        ValidatedMistakeImage validatedImage = imageValidator.validate(image);
        if (questionText == null && validatedImage == null) {
            throw new MistakeContentRequiredException();
        }

        UUID userId = currentUserProvider.getUserId();
        UUID mistakeId = UUID.randomUUID();
        Instant now = clock.instant();
        String objectKey = uploadImage(userId, mistakeId, validatedImage);
        int creditsRemaining = deductCredit(userId);
        PersonalQuestionEntity mistake = buildMistake(
                request, questionText, validatedImage, objectKey, userId, mistakeId, now
        );
        requireSingleRow(mistakeMapper.insert(mistake), "创建错题失败");
        requireSingleRow(
                outboxMapper.insertAnalysisRequested(UUID.randomUUID(), mistakeId, now),
                "创建分析任务失败"
        );
        return new CreateMistakeVO(toSummary(mistake), creditsRemaining);
    }

    /**
     * 标记或取消标记“已掌握”
     *
     * <p>主要流程：更新状态。
     * 方法返回时仅表示是否成功。</p>
     */
    @Override
    @Transactional
    public MistakeSummaryVO updateMastery(UUID id, @Valid UpdateMasteryDTO updateMasteryDTO) {
        UUID userId = currentUserProvider.getUserId();
        PersonalQuestionEntity mistake = mistakeMapper
                .selectActiveByIdAndUserId(id, userId);
        if (mistake == null) {
            throw new MistakeNotFoundException();
        }
        if (mistake.getAnalysisStatus() != AnalysisStatus.COMPLETED) {
            throw new AnalysisNotCompletedException();
        }
        if (mistake.isMastered() != updateMasteryDTO.mastered()) {
            int ret = mistakeMapper.updateMasteredByIdAndUserId(
                    id, userId, updateMasteryDTO.mastered()
            );
            if (ret == 0) {
                throw new MistakeNotFoundException();
            }
            mistake.setMastered(updateMasteryDTO.mastered());
        }

        return new MistakeSummaryVO(
                mistake.getId(),
                mistake.getTitle(),
                mistake.getSubject(),
                mistake.getChapter(),
                mistake.getQuestionType(),
                mistake.getAnalysisStatus(),
                mistake.isMastered(),
                mistake.getCreatedAt()
        );
    }


    /**
     * 在当前事务中锁定用户额度记录并扣减一次额度，防止并发请求超扣。
     */
    private int deductCredit(UUID userId) {
        Integer credits = mistakeMapper.selectActiveCreditsForUpdate(userId);
        if (credits == null) {
            throw new MistakeUserUnavailableException();
        }
        if (credits <= 0) {
            throw new InsufficientCreditsException();
        }
        requireSingleRow(mistakeMapper.decrementCredit(userId), "扣减分析额度失败");
        return credits - 1;
    }

    /**
     * 将通过签名校验的图片上传至私有 Bucket，并登记事务回滚补偿。
     */
    private String uploadImage(UUID userId, UUID mistakeId, ValidatedMistakeImage image) {
        if (image == null) {
            return null;
        }
        String objectKey = "users/%s/mistakes/%s/original.%s".formatted(
                userId, mistakeId, image.extension()
        );
        imageStorageService.put(objectKey, image.contentType(), image.content());
        registerRollbackCleanup(objectKey);
        return objectKey;
    }

    /**
     * 数据库事务未提交时删除已上传对象，避免产生没有对应错题记录的孤立文件。
     */
    private void registerRollbackCleanup(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteUploadedObject(objectKey);
            throw new IllegalStateException("创建错题必须在事务中执行");
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            deleteUploadedObject(objectKey);
                        }
                    }
                }
        );
    }

    /**
     * 执行尽力而为的图片补偿；清理失败只记录日志，不覆盖原始事务异常。
     */
    private void deleteUploadedObject(String objectKey) {
        try {
            imageStorageService.delete(objectKey);
        } catch (MistakeStorageException exception) {
            log.warn("Failed to clean up uploaded mistake image key={}", objectKey, exception);
        }
    }

    /**
     * 组装初始错题实体。新建错题固定处于 ACTIVE、QUEUED、未掌握状态。
     */
    private PersonalQuestionEntity buildMistake(
            CreateMistakeDTO request,
            String questionText,
            ValidatedMistakeImage image,
            String objectKey,
            UUID userId,
            UUID mistakeId,
            Instant now
    ) {
        PersonalQuestionEntity mistake = new PersonalQuestionEntity();
        mistake.setId(mistakeId);
        mistake.setUserId(userId);
        mistake.setTitle(resolveTitle(request.title(), questionText));
        mistake.setSubject(request.subject().trim());
        mistake.setChapter(normalizeNullable(request.chapter()));
        mistake.setQuestionType(request.type().trim());
        mistake.setStemText(questionText);
        mistake.setUserAnswer(normalizeNullable(request.userAnswer()));
        if (image != null) {
            mistake.setImageObjectKey(objectKey);
            mistake.setImageOriginalName(image.originalName());
            mistake.setImageContentType(image.contentType());
            mistake.setImageSize(image.size());
            mistake.setImageSha256(image.sha256());
        }
        mistake.setStatus("ACTIVE");
        mistake.setAnalysisStatus(AnalysisStatus.QUEUED);
        mistake.setMastered(false);
        mistake.setVersion(0);
        mistake.setCreatedAt(now);
        mistake.setUpdatedAt(now);
        return mistake;
    }

    /** 为私有图片生成短期有效的浏览器可访问地址。 */
    private MistakeImageVO toImage(String objectKey) {
        if (objectKey == null) {
            return null;
        }
        PresignedImage image = imageStorageService.createReadUrl(objectKey);
        return new MistakeImageVO(image.url(), image.expiresAt());
    }

    /** 仅在分析完成后返回分析内容，排队中或失败时返回 null。 */
    private MistakeAnalysisVO toAnalysis(PersonalQuestionEntity mistake) {
        if (mistake.getAnalysisStatus() != AnalysisStatus.COMPLETED) {
            return null;
        }
        return new MistakeAnalysisVO(
                mistake.getAnalysisSummary(), mistake.getAnalysisKnowledge(),
                mistake.getAnalysisSteps(), mistake.getAnalysisSuggestion(),
                mistake.getAnalysisAnswer(), mistake.getAnalysisConfidence()
        );
    }

    /** 将持久化实体转换为创建接口所需的摘要。 */
    private MistakeSummaryVO toSummary(PersonalQuestionEntity mistake) {
        return new MistakeSummaryVO(
                mistake.getId(), mistake.getTitle(), mistake.getSubject(),
                mistake.getChapter(), mistake.getQuestionType(), mistake.getAnalysisStatus(),
                mistake.isMastered(), mistake.getCreatedAt()
        );
    }

    /** 优先使用客户端标题；未提供时从题目文本生成，纯图片错题使用默认标题。 */
    private String resolveTitle(String submittedTitle, String questionText) {
        String title = normalizeNullable(submittedTitle);
        if (title != null) {
            return title;
        }
        if (questionText == null) {
            return "未命名错题";
        }
        String generated = questionText.replaceAll("\\s+", " ").trim();
        return generated.length() <= MAX_TITLE_LENGTH
                ? generated
                : generated.substring(0, MAX_TITLE_LENGTH);
    }

    /** 将空白字符串统一归一化为 null，避免数据库中混用空串和 null。 */
    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /** 所有单行写操作都必须恰好影响一行，否则触发事务回滚。 */
    private void requireSingleRow(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
