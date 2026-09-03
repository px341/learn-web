package com.learn.mistakeservice.messaging;

import com.learn.mistakeservice.config.MistakeMessagingConfiguration;
import com.learn.mistakeservice.dto.MistakeAnalysisMessage;
import com.learn.mistakeservice.entity.MistakeOutboxEventEntity;
import com.learn.mistakeservice.mapper.MistakeOutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * 错题分析 Outbox 事件发布器。
 *
 * <p>创建错题时，业务记录、额度扣减和 Outbox 事件在同一个数据库事务中提交。本组件随后
 * 定时锁定待发布事件并发送到 RabbitMQ；只有 Broker publisher confirm 成功后才把事件标为
 * {@code PUBLISHED}。发布失败时事件仍为 {@code PENDING}，同时记录失败摘要并更新下次尝试
 * 时间，从而避免数据库提交成功但消息永久丢失。</p>
 *
 * <p>数据库提交与消息发送无法组成同一个原子事务，所以整体提供的是至少一次投递语义：
 * 极端情况下同一事件可能重复发送。下游分析服务必须依靠错题状态和版本号保证幂等。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MistakeOutboxPublisher {

    /** 单次事务最多锁定并尝试发布的 Outbox 事件数量。 */
    private static final int BATCH_SIZE = 50;

    /** 持久化发布失败摘要时允许的最大字符数。 */
    private static final int MAX_ERROR_LENGTH = 1000;

    /** 等待 RabbitMQ publisher confirm 的最长时间。 */
    private static final long PUBLISH_CONFIRM_TIMEOUT_MILLIS = 5_000;

    /** 锁定待发布事件并维护发布状态、次数和下次尝试时间。 */
    private final MistakeOutboxMapper outboxMapper;

    /** 负责消息转换、发送和 publisher confirm。 */
    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布一批已经到达尝试时间的 Outbox 事件。
     *
     * <p>查询使用 {@code FOR UPDATE SKIP LOCKED}，因此多个实例可以并行处理不同事件。该方法
     * 在数据库事务内运行，使事件锁一直保持到发布结果写回；单个事件失败不会阻止同批其他
     * 事件尝试发布。</p>
     */
    @Scheduled(
            fixedDelayString = "${messaging.outbox.fixed-delay:2s}",
            initialDelayString = "${messaging.outbox.initial-delay:30s}"
    )
    @Transactional
    public void publishPending() {
        List<MistakeOutboxEventEntity> events = outboxMapper
                .selectPendingForUpdate(BATCH_SIZE);
        for (MistakeOutboxEventEntity event : events) {
            try {
                publish(event);
                outboxMapper.markPublished(event.getId(), Instant.now());
            } catch (RuntimeException exception) {
                log.warn("Failed to publish mistake outbox event id={} attempts={}",
                        event.getId(), event.getAttempts(), exception);
                outboxMapper.markRetry(event.getId(), abbreviate(exception.getMessage()));
            }
        }
    }

    /**
     * 把一条 Outbox 事件转换为分析请求并等待 Broker 确认。
     *
     * <p>消息体只包含错题 ID；Outbox 事件 ID 写入 AMQP message ID，事件类型写入 header，
     * 便于日志关联和后续排查。{@link RabbitTemplate#invoke} 为本次发送提供独立的通道上下文，
     * 随后的 confirm 等待对应同一次调用。</p>
     *
     * @param event 已被当前事务锁定的待发布事件
     * @throws RuntimeException 消息转换、发送失败或在超时时间内未收到确认时
     */
    private void publish(MistakeOutboxEventEntity event) {
        rabbitTemplate.invoke(operations -> {
            operations.convertAndSend(
                    MistakeMessagingConfiguration.ANALYSIS_EXCHANGE,
                    MistakeMessagingConfiguration.ANALYSIS_ROUTING_KEY,
                    new MistakeAnalysisMessage(event.getMistakeId()),
                    message -> {
                        MessageProperties properties = message.getMessageProperties();
                        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                        properties.setContentEncoding(StandardCharsets.UTF_8.name());
                        properties.setMessageId(event.getId().toString());
                        properties.setHeader("eventType", event.getEventType());
                        return message;
                    }
            );
            operations.waitForConfirmsOrDie(PUBLISH_CONFIRM_TIMEOUT_MILLIS);
            return null;
        });
    }

    /**
     * 将发布异常信息转换为适合数据库字段的有界摘要。
     *
     * @param message 原始异常信息，可以为空
     * @return 非空且不超过 {@link #MAX_ERROR_LENGTH} 个字符的摘要
     */
    private String abbreviate(String message) {
        String value = message == null ? "RabbitMQ publish failed" : message;
        return value.length() <= MAX_ERROR_LENGTH
                ? value
                : value.substring(0, MAX_ERROR_LENGTH);
    }
}
