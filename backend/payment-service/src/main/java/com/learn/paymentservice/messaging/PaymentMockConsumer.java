package com.learn.paymentservice.messaging;

import com.learn.paymentservice.service.PaymentMockCoordinator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 使用注解声明 RabbitMQ 拓扑并消费模拟支付订单。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.mock.enabled", havingValue = "true")
public class PaymentMockConsumer {

    private final PaymentMockCoordinator paymentMockCoordinator;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(value = "${payment.mock.queue}", durable = "true"),
                    exchange = @Exchange(
                            value = "${payment.mock.exchange}",
                            type = ExchangeTypes.DIRECT,
                            durable = "true"
                    ),
                    key = "${payment.mock.routing-key}"
            ),
            concurrency = "${payment.mock.listener-concurrency:1}"
    )
    public void consume(String orderIdPayload) {
        UUID orderId;
        try {
            orderId = UUID.fromString(orderIdPayload);
        } catch (IllegalArgumentException exception) {
            // 非法消息无法通过重试恢复，记录后正常确认，避免形成毒消息循环。
            log.warn("忽略非法模拟支付消息, payload={}", orderIdPayload);
            return;
        }

        paymentMockCoordinator.completeMockPayment(orderId);
        log.info("模拟支付消息处理完成, orderId={}", orderId);
    }
}
