package com.learn.paymentservice.exception;

public class PlanNotFoundException extends RuntimeException {
    public PlanNotFoundException() {
        super("支付套餐不存在或已停用");
    }
}
