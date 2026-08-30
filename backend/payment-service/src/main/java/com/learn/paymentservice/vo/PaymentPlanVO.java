package com.learn.paymentservice.vo;

/** 支付页展示的服务端套餐配置。 */
public record PaymentPlanVO(
        String id,
        String name,
        int credits,
        int priceFen,
        String description,
        boolean recommended
) {
}
