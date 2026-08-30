package com.learn.paymentservice.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** 支付订单状态；数据库使用大写枚举名，JSON 使用小写值。 */
public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    CANCELLED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static PaymentStatus fromValue(String value) {
        return PaymentStatus.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
