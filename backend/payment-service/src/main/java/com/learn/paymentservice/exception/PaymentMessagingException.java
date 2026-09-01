package com.learn.paymentservice.exception;

/** RabbitMQ 暂时不可用或消息投递失败。 */
public class PaymentMessagingException extends RuntimeException {
    public PaymentMessagingException(Throwable cause) {
        super("支付消息服务暂时不可用", cause);
    }
}
