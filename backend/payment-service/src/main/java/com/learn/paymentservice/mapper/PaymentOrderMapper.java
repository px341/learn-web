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

    int insert(PaymentOrderEntity order);

    int incrementCredits(@Param("userId") UUID userId, @Param("credits") int credits);
}
