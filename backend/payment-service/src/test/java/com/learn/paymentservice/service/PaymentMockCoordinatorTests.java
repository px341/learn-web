package com.learn.paymentservice.service;

import com.learn.paymentservice.infrastructure.RedisLockExecutor;
import com.learn.paymentservice.model.PaymentStatus;
import com.learn.paymentservice.vo.MockPaymentVO;
import com.learn.security.currentuser.CurrentUserProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentMockCoordinatorTests {

    @Test
    void createsPaymentInsidePerUserRedisLock() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        RedisLockExecutor lockExecutor = mock(RedisLockExecutor.class);
        PaymentService paymentService = mock(PaymentService.class);
        MockPaymentVO expected = new MockPaymentVO(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                PaymentStatus.PENDING,
                "starter",
                "进阶学习包",
                10,
                800,
                "CNY",
                Instant.parse("2026-08-22T02:10:00Z")
        );
        when(currentUserProvider.getUserId()).thenReturn(userId);
        when(paymentService.createMockPayment("starter", "request-1"))
                .thenReturn(expected);
        when(lockExecutor.execute(
                eq("payment:mock:create:" + userId),
                org.mockito.ArgumentMatchers.<Supplier<MockPaymentVO>>any()
        ))
                .thenAnswer(invocation -> invocation.<Supplier<MockPaymentVO>>getArgument(1).get());

        PaymentMockCoordinator coordinator = new PaymentMockCoordinator(
                currentUserProvider,
                lockExecutor,
                paymentService
        );

        MockPaymentVO result = coordinator.createMockPayment("starter", "request-1");

        assertThat(result).isSameAs(expected);
        verify(paymentService).createMockPayment("starter", "request-1");
    }

    @Test
    void completesPaymentInsidePerOrderRedisLock() {
        UUID orderId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        RedisLockExecutor lockExecutor = mock(RedisLockExecutor.class);
        PaymentService paymentService = mock(PaymentService.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(lockExecutor).execute(eq("payment:mock:complete:" + orderId), any(Runnable.class));
        PaymentMockCoordinator coordinator = new PaymentMockCoordinator(
                currentUserProvider,
                lockExecutor,
                paymentService
        );

        coordinator.completeMockPayment(orderId);

        verify(paymentService).completeMockPayment(orderId);
    }
}
