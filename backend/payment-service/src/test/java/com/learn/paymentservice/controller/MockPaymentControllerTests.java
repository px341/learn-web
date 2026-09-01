package com.learn.paymentservice.controller;

import com.learn.paymentservice.messaging.PaymentMockPublisher;
import com.learn.paymentservice.model.PaymentStatus;
import com.learn.paymentservice.service.PaymentMockCoordinator;
import com.learn.paymentservice.vo.MockPaymentVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MockPaymentControllerTests {

    @Test
    void acceptsOrderAndPublishesItsId() throws Exception {
        UUID orderId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PaymentMockCoordinator coordinator = mock(PaymentMockCoordinator.class);
        when(coordinator.createMockPayment("starter", "request-1"))
                .thenReturn(new MockPaymentVO(
                        orderId,
                        PaymentStatus.PENDING,
                        "starter",
                        "进阶学习包",
                        10,
                        800,
                        "CNY",
                        Instant.parse("2026-08-22T02:10:00Z")
                ));
        PaymentMockPublisher publisher = mock(PaymentMockPublisher.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new MockPaymentController(coordinator, publisher)
        ).build();

        mockMvc.perform(post("/api/payments/mock")
                        .header("Idempotency-Key", "request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"starter\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.planId").value("starter"))
                .andExpect(jsonPath("$.data.credits").value(10))
                .andExpect(jsonPath("$.data.amountFen").value(800));

        verify(coordinator).createMockPayment("starter", "request-1");
        verify(publisher).publish(orderId);
    }
}
