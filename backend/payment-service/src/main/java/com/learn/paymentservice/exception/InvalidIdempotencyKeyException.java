package com.learn.paymentservice.exception;

public class InvalidIdempotencyKeyException extends RuntimeException {
    public InvalidIdempotencyKeyException() {
        super("Idempotency-Key 不能为空且不能超过 128 个字符");
    }
}
