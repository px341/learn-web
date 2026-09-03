package com.learn.mistakeservice.messaging;

import com.learn.mistakeservice.entity.PersonalQuestionEntity;
import com.learn.mistakeservice.mapper.MistakeMapper;
import com.learn.mistakeservice.mapper.MistakeOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 分析任务租约恢复器。
 *
 * <p>Worker 领取任务后会把状态从 {@code QUEUED} 改为 {@code ANALYZING}。如果进程在
 * 写回结果前崩溃，消息可能已经失去继续执行的消费者，任务则会长期停留在分析中。本组件
 * 定期查找更新时间早于租约截止时间的任务，把它们恢复为 {@code QUEUED}，并在同一事务中
 * 写入新的 Outbox 事件，使任务能够再次发布到 RabbitMQ。</p>
 *
 * <p>查询使用数据库行锁和 {@code SKIP LOCKED}，状态恢复使用版本号条件更新，因此多个
 * 服务实例可以同时运行恢复任务，而不会重复接管同一条记录或覆盖正在完成的新结果。</p>
 */
@Slf4j
@Component
public class AnalysisLeaseRecovery {

    /** 单次扫描最多处理的任务数，限制事务持锁时间和 Outbox 写入量。 */
    private static final int BATCH_SIZE = 50;

    /** 查询并恢复过期分析状态。 */
    private final MistakeMapper mistakeMapper;

    /** 为成功恢复的任务创建新的可靠发布事件。 */
    private final MistakeOutboxMapper outboxMapper;

    /** {@code ANALYZING} 状态允许无进展持续的最长时间。 */
    private final Duration leaseTimeout;

    /**
     * 创建租约恢复器。
     *
     * @param mistakeMapper 错题状态持久化入口
     * @param outboxMapper Outbox 事件持久化入口
     * @param leaseTimeout 判断任务失去租约前允许的最长无更新时长
     */
    public AnalysisLeaseRecovery(
            MistakeMapper mistakeMapper,
            MistakeOutboxMapper outboxMapper,
            @Value("${analysis.lease-timeout:10m}") Duration leaseTimeout
    ) {
        this.mistakeMapper = mistakeMapper;
        this.outboxMapper = outboxMapper;
        this.leaseTimeout = leaseTimeout;
    }

    /**
     * 扫描并恢复一批超过租约的分析任务。
     *
     * <p>状态更新和新 Outbox 事件写入处于同一数据库事务中：只有版本匹配且仍为
     * {@code ANALYZING} 的记录才会恢复；恢复成功后必须同时写入事件。任一步骤失败都会
     * 回滚，避免出现任务已经排队但没有消息事件的状态。</p>
     */
    @Scheduled(
            fixedDelayString = "${analysis.recovery.fixed-delay:1m}",
            initialDelayString = "${analysis.recovery.initial-delay:1m}"
    )
    @Transactional
    public void recoverExpired() {
        Instant now = Instant.now();
        List<PersonalQuestionEntity> expired = mistakeMapper.selectExpiredAnalysesForUpdate(
                now.minus(leaseTimeout), BATCH_SIZE
        );
        int recovered = 0;
        for (PersonalQuestionEntity question : expired) {
            int updated = mistakeMapper.recoverExpiredAnalysis(
                    question.getId(), question.getVersion()
            );
            if (updated == 1) {
                outboxMapper.insertAnalysisRequested(UUID.randomUUID(), question.getId(), now);
                recovered++;
            }
        }
        if (recovered > 0) {
            log.warn("Recovered {} expired mistake analysis task(s)", recovered);
        }
    }
}
