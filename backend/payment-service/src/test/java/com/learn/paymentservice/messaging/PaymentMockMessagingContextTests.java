package com.learn.paymentservice.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "payment.mock.enabled=true",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class PaymentMockMessagingContextTests {

    @Autowired
    private PaymentMockPublisher publisher;

    @Autowired
    private PaymentMockConsumer consumer;

    @Test
    void registersAnnotatedMockMessagingComponents() {
        assertThat(publisher).isNotNull();
        assertThat(consumer).isNotNull();
    }
}
