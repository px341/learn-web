package com.learn.paymentservice.exception;

/** Redis 不可用或在等待时间内未取得模拟支付分布式锁。 */
public class PaymentLockUnavailableException extends RuntimeException {
    public PaymentLockUnavailableException() {
        super("支付并发控制服务暂时不可用，请稍后重试");
    }

    public PaymentLockUnavailableException(Throwable cause) {
        super("支付并发控制服务暂时不可用，请稍后重试", cause);
    }
}
