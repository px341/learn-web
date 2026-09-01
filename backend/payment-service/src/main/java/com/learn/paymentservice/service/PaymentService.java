package com.learn.paymentservice.service;

import com.learn.paymentservice.vo.PaymentPlanVO;
import com.learn.paymentservice.vo.MockPaymentVO;

import java.util.List;
import java.util.UUID;

public interface PaymentService {

    List<PaymentPlanVO> getPlans();

    MockPaymentVO createMockPayment(String planId, String idempotencyKey);

    void completeMockPayment(UUID orderId);
}
