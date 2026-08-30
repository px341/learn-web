package com.learn.paymentservice.exception;

public class PaymentUserUnavailableException extends RuntimeException {
    public PaymentUserUnavailableException() {
        super("当前用户不可用");
    }
}
