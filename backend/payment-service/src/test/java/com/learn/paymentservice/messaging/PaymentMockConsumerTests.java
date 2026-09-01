package com.learn.paymentservice.messaging;

import com.learn.paymentservice.service.PaymentMockCoordinator;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PaymentMockConsumerTests {

    @Test
    void completesOrderFromValidMessage() {
        UUID orderId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PaymentMockCoordinator coordinator = mock(PaymentMockCoordinator.class);
        PaymentMockConsumer consumer = new PaymentMockConsumer(coordinator);

        consumer.consume(orderId.toString());

        verify(coordinator).completeMockPayment(orderId);
    }

    @Test
    void acknowledgesInvalidPayloadWithoutCallingService() {
        PaymentMockCoordinator coordinator = mock(PaymentMockCoordinator.class);
        PaymentMockConsumer consumer = new PaymentMockConsumer(coordinator);

        consumer.consume("not-a-uuid");

        verify(coordinator, never()).completeMockPayment(
                org.mockito.ArgumentMatchers.any()
        );
    }
}
