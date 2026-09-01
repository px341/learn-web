package com.learn.paymentservice.vo;

import com.learn.paymentservice.entity.PaymentOrderEntity;
import com.learn.paymentservice.model.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * 模拟支付请求受理后的订单视图。
 *
 * <p>支付和额度入账通过 MQ 异步完成，因此该响应只表示订单已经受理。
 * 客户端应根据 {@code orderId} 查询后续状态，不能把本响应当作额度已到账。</p>
 */
public record MockPaymentVO(
        UUID orderId,
        PaymentStatus status,
        String planId,
        String planName,
        int credits,
        int amountFen,
        String currency,
        Instant createdAt
) {

    /** 从支付订单快照创建对外响应，不暴露用户 ID 和幂等键。 */
    public static MockPaymentVO from(PaymentOrderEntity order) {
        return new MockPaymentVO(
                order.getId(),
                order.getStatus(),
                order.getPlanId(),
                order.getPlanName(),
                order.getCreditsAdded(),
                order.getAmountFen(),
                order.getCurrency(),
                order.getCreatedAt()
        );
    }
}
