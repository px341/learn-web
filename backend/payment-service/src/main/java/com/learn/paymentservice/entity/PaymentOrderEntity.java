package com.learn.paymentservice.entity;

import com.learn.paymentservice.model.PaymentStatus;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code payment_orders} 表的内部持久化对象。
 *
 * <p>套餐名称、额度、金额和币种均保存下单时的服务端快照。异步支付请求创建时
 * 状态为 {@link PaymentStatus#PENDING}；支付成功事件处理完成后更新为
 * {@link PaymentStatus#PAID}，并设置 {@code paidAt}。</p>
 */
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
