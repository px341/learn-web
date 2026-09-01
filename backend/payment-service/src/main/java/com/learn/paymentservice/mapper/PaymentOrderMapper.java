package com.learn.paymentservice.mapper;

import com.learn.paymentservice.entity.PaymentOrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface PaymentOrderMapper {

    Integer selectActiveCreditsForUpdate(UUID userId);

    PaymentOrderEntity selectByUserIdAndIdempotencyKey(
            @Param("userId") UUID userId,
            @Param("idempotencyKey") String idempotencyKey
    );

    PaymentOrderEntity selectByIdForUpdate(UUID orderId);

    int insert(PaymentOrderEntity order);

    int incrementCredits(@Param("userId") UUID userId, @Param("credits") int credits);

    int markPaid(@Param("orderId") UUID orderId, @Param("paidAt") java.time.Instant paidAt);

    int markFailed(
            @Param("orderId") UUID orderId,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage
    );
}
