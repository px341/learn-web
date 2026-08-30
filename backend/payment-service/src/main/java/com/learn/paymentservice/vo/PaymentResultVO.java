package com.learn.paymentservice.vo;

import com.learn.paymentservice.model.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

/** 支付完成后的订单状态和最新额度。 */
public record PaymentResultVO(
        UUID orderId,
        PaymentStatus status,
        int creditsAdded,
        int creditsRemaining,
        Instant paidAt
) {
}
