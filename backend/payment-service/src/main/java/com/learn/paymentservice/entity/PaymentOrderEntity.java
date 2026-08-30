package com.learn.paymentservice.entity;

import com.learn.paymentservice.model.PaymentStatus;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/** payment_orders 表的内部持久化对象，包含下单时的套餐和金额快照。 */
@Data
public class PaymentOrderEntity {
    private UUID id;
    private UUID userId;
    private String planId;
    private String idempotencyKey;

    private String planName;
    private Integer creditsAdded;
    private Integer amountFen;
    private String currency;

    private String provider;
    private String providerOrderId;
    private PaymentStatus status;
    private String failureCode;
    private String failureMessage;
    private Instant paidAt;
    private Instant createdAt;
    private Instant updatedAt;
}
