package com.learn.paymentservice.exception;

public class PaymentIdempotencyConflictException extends RuntimeException {
    public PaymentIdempotencyConflictException() {
        super("该 Idempotency-Key 已用于其他支付请求");
    }
}
