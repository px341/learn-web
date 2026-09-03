package com.learn.mistakeservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.mistakeservice.config.MistakeMessagingConfiguration;
import com.learn.mistakeservice.dto.MistakeAnalysisMessage;
import com.learn.mistakeservice.service.MistakeAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 分析失败队列消费者。
 *
 * <p>主队列消息在临时异常重试耗尽后，由 {@code RepublishMessageRecoverer} 转发到失败队列。
 * 本消费者读取原始 AMQP 消息体，恢复其中的错题 ID，并请求业务层把仍处于
 * {@code QUEUED} 的任务终结为 {@code FAILED}，从而让前端停止轮询并展示明确原因。</p>
 *
 * <p>失败队列中的消息不再继续重试。无法解析、缺少 ID 或不符合消息契约的毒消息只记录
 * message ID 后正常确认，防止它们在失败队列中形成无限消费循环。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MistakeAnalysisDeadLetterConsumer {

    /** 重试耗尽后写入错题记录、可安全展示给用户的统一失败文案。 */
    private static final String FAILURE_MESSAGE = "分析服务多次重试仍不可用，请稍后重新上传";

    /** 将任务从排队状态安全地终结为失败状态。 */
    private final MistakeAnalysisService mistakeAnalysisService;

    /** 解析重试恢复器重新发布的原始 JSON 消息体。 */
    private final ObjectMapper objectMapper;

    /**
     * 消费并终结一条重试耗尽的分析任务。
     *
     * <p>这里接收 {@link Message} 而不是直接接收 DTO，是为了对损坏消息进行本地解析和吞吐，
     * 避免转换阶段异常阻止监听方法获得 message ID。业务层的条件更新可防止迟到的死信覆盖
     * 已经完成或被其他 Worker 重新领取的任务。</p>
     *
     * @param message 由重试恢复器转发的原始 AMQP 消息
     */
    @RabbitListener(queues = MistakeMessagingConfiguration.ANALYSIS_FAILED_QUEUE)
    public void consume(Message message) {
        try {
            MistakeAnalysisMessage payload = objectMapper.readValue(
                    message.getBody(), MistakeAnalysisMessage.class
            );
            mistakeAnalysisService.failAfterRetries(payload.mistakeId(), FAILURE_MESSAGE);
            log.warn("Mistake analysis retries exhausted id={}", payload.mistakeId());
        } catch (IOException | IllegalArgumentException | NullPointerException exception) {
            // 毒消息无法通过再次投递修复；记录消息 ID 后确认，避免死信队列无限循环。
            log.error("Discarded invalid mistake analysis dead letter messageId={}",
                    message.getMessageProperties().getMessageId(), exception);
        }
    }
}
