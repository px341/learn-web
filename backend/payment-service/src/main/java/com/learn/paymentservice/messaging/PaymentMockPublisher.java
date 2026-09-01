package com.learn.paymentservice.messaging;

import com.learn.paymentservice.exception.PaymentMessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.mock.enabled", havingValue = "true")
public class PaymentMockPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${payment.mock.exchange}")
    private String exchange;

    @Value("${payment.mock.routing-key}")
    private String routingKey;

    /**
     * 投递待完成的模拟支付订单。消息体只包含订单 ID，其他可信信息从数据库读取。
     */
    public void publish(UUID orderId) {
        try {
            rabbitTemplate.convertAndSend(
                    exchange,
                    routingKey,
                    orderId.toString(),
                    message -> {
                        message.getMessageProperties()
                                .setMessageId(orderId.toString());
                        return message;
                    }
            );
            log.info("已投递模拟支付订单, orderId={}", orderId);
        } catch (AmqpException exception) {
            throw new PaymentMessagingException(exception);
        }
    }
}
