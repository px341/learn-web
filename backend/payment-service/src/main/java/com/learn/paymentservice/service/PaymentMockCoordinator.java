package com.learn.paymentservice.service;

import com.learn.paymentservice.infrastructure.RedisLockExecutor;
import com.learn.paymentservice.vo.MockPaymentVO;
import com.learn.security.currentuser.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** 在 Redis 分布式锁内调用事务化支付服务，确保锁覆盖到数据库事务提交。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.mock.enabled", havingValue = "true")
public class PaymentMockCoordinator {

    private static final String CREATE_LOCK_PREFIX = "payment:mock:create:";
    private static final String COMPLETE_LOCK_PREFIX = "payment:mock:complete:";

    private final CurrentUserProvider currentUserProvider;
    private final RedisLockExecutor redisLockExecutor;
    private final PaymentService paymentService;

    public MockPaymentVO createMockPayment(String planId, String idempotencyKey) {
        UUID userId = currentUserProvider.getUserId();
        return redisLockExecutor.execute(
                CREATE_LOCK_PREFIX + userId,
                () -> paymentService.createMockPayment(planId, idempotencyKey)
        );
    }

    public void completeMockPayment(UUID orderId) {
        redisLockExecutor.execute(
                COMPLETE_LOCK_PREFIX + orderId,
                () -> paymentService.completeMockPayment(orderId)
        );
    }
}
