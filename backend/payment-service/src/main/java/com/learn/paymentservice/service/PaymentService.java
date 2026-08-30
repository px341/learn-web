package com.learn.paymentservice.service;

import com.learn.paymentservice.vo.PaymentPlanVO;
import com.learn.paymentservice.vo.PaymentResultVO;

import java.util.List;

public interface PaymentService {

    List<PaymentPlanVO> getPlans();

    PaymentResultVO mockPayment(String planId, String idempotencyKey);
}
