package com.learn.mistakeservice.messaging;

import com.learn.mistakeservice.config.MistakeMessagingConfiguration;
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

/** 将已提交的 Outbox 事件以至少一次语义发布到 RabbitMQ。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MistakeOutboxPublisher {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ERROR_LENGTH = 1000;
    private static final long PUBLISH_CONFIRM_TIMEOUT_MILLIS = 5_000;

    private final MistakeOutboxMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;

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

    private void publish(MistakeOutboxEventEntity event) {
        String payload = "{\"mistakeId\":\"%s\"}".formatted(event.getMistakeId());
        rabbitTemplate.invoke(operations -> {
            operations.convertAndSend(
                    MistakeMessagingConfiguration.ANALYSIS_EXCHANGE,
                    MistakeMessagingConfiguration.ANALYSIS_ROUTING_KEY,
                    payload.getBytes(StandardCharsets.UTF_8),
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

    private String abbreviate(String message) {
        String value = message == null ? "RabbitMQ publish failed" : message;
        return value.length() <= MAX_ERROR_LENGTH
                ? value
                : value.substring(0, MAX_ERROR_LENGTH);
    }
}
