package com.learn.mistakeservice.mapper;

import com.learn.mistakeservice.entity.PersonalQuestionEntity;
import com.learn.mistakeservice.vo.MistakeAnalysisVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface MistakeMapper {

    /**
     * 同时限定主键和用户 ID；其他用户的记录与不存在记录都返回 null。
     */
    PersonalQuestionEntity selectActiveByIdAndUserId(
            @Param("id") UUID id,
            @Param("userId") UUID userId
    );

    List<PersonalQuestionEntity> selectActiveByUserId(
            @Param("userId") UUID userId,
            @Param("keyword") String keyword,
            @Param("subject") String subject,
            @Param("analysisStatus") String analysisStatus,
            @Param("mastered") Boolean mastered,
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("ascending") boolean ascending
    );

    long countActiveByUserId(
            @Param("userId") UUID userId,
            @Param("keyword") String keyword,
            @Param("subject") String subject,
            @Param("analysisStatus") String analysisStatus,
            @Param("mastered") Boolean mastered
    );

    int insert(PersonalQuestionEntity mistake);

    Integer selectActiveCreditsForUpdate(@Param("userId") UUID userId);

    int decrementCredit(@Param("userId") UUID userId);

    int updateMasteredByIdAndUserId(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("mastered") boolean mastered
    );

    /** Worker 根据消息中的错题 ID 读取分析所需内容。 */
    PersonalQuestionEntity selectByIdForAnalysis(@Param("id") UUID id);

    /** 原子地把 QUEUED 任务切换为 ANALYZING；返回 0 表示任务已被领取或不可分析。 */
    int claimAnalysis(@Param("id") UUID id);

    /** 使用领取任务后的版本号写入分析结果，避免旧 Worker 覆盖新结果。 */
    int markAnalysisCompleted(
            @Param("id") UUID id,
            @Param("expectedVersion") int expectedVersion,
            @Param("analysis") MistakeAnalysisVO analysis
    );

    /** 将当前分析尝试标记为失败。failureMessage 必须是非空的安全摘要。 */
    int markAnalysisFailed(
            @Param("id") UUID id,
            @Param("expectedVersion") int expectedVersion,
            @Param("failureMessage") String failureMessage
    );

    /** 临时异常后将本次尝试退回队列，使消息重试能够重新领取。 */
    int releaseAnalysisForRetry(
            @Param("id") UUID id,
            @Param("expectedVersion") int expectedVersion
    );

    /** 锁定一批超过租约的 ANALYZING 任务，供恢复器逐条恢复并重建 Outbox 事件。 */
    List<PersonalQuestionEntity> selectExpiredAnalysesForUpdate(
            @Param("expiredBefore") Instant expiredBefore,
            @Param("limit") int limit
    );

    /** 使用版本号把一条超时任务恢复为 QUEUED。 */
    int recoverExpiredAnalysis(
            @Param("id") UUID id,
            @Param("expectedVersion") int expectedVersion
    );

    /** 重试耗尽后把已退回 QUEUED 的任务终结为 FAILED。 */
    int markQueuedAnalysisFailed(
            @Param("id") UUID id,
            @Param("failureMessage") String failureMessage
    );
}
