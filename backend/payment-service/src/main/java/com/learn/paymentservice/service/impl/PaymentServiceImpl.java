package com.learn.paymentservice.service.impl;

import com.learn.paymentservice.entity.PaymentOrderEntity;
import com.learn.paymentservice.entity.PaymentPlanEntity;
import com.learn.paymentservice.exception.InvalidIdempotencyKeyException;
import com.learn.paymentservice.exception.PaymentIdempotencyConflictException;
import com.learn.paymentservice.exception.PaymentUserUnavailableException;
import com.learn.paymentservice.exception.PlanNotFoundException;
import com.learn.paymentservice.mapper.PaymentOrderMapper;
import com.learn.paymentservice.mapper.PaymentPlanMapper;
import com.learn.paymentservice.model.PaymentStatus;
import com.learn.paymentservice.service.PaymentService;
import com.learn.paymentservice.vo.PaymentPlanVO;
import com.learn.paymentservice.vo.PaymentResultVO;
import com.learn.security.currentuser.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final CurrentUserProvider currentUserProvider;
    private final PaymentPlanMapper paymentPlanMapper;
    private final PaymentOrderMapper paymentOrderMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PaymentPlanVO> getPlans() {
        return paymentPlanMapper.selectActivePlans().stream()
                .map(this::toPlanVO)
                .toList();
    }

    @Override
    @Transactional
    public PaymentResultVO mockPayment(String planId, String idempotencyKey) {
        String normalizedPlanId = normalizePlanId(planId);
        String normalizedKey = validateIdempotencyKey(idempotencyKey);
        UUID userId = currentUserProvider.getUserId();

        // 同一用户的支付请求串行化，避免并发重试重复创建订单或重复入账。
        Integer creditsBeforePayment = paymentOrderMapper.selectActiveCreditsForUpdate(userId);
        if (creditsBeforePayment == null) {
            throw new PaymentUserUnavailableException();
        }

        PaymentOrderEntity existing = paymentOrderMapper
                .selectByUserIdAndIdempotencyKey(userId, normalizedKey);
        if (existing != null) {
            if (!existing.getPlanId().equals(normalizedPlanId)) {
                throw new PaymentIdempotencyConflictException();
            }
            return toResultVO(existing, creditsBeforePayment);
        }

        PaymentPlanEntity plan = paymentPlanMapper.selectActiveById(normalizedPlanId);
        if (plan == null) {
            throw new PlanNotFoundException();
        }

        Instant now = Instant.now();
        PaymentOrderEntity order = new PaymentOrderEntity();
        order.setId(UUID.randomUUID());
        order.setUserId(userId);
        order.setPlanId(plan.getId());
        order.setIdempotencyKey(normalizedKey);
        order.setPlanName(plan.getName());
        order.setCreditsAdded(plan.getCredits());
        order.setAmountFen(plan.getPriceFen());
        order.setCurrency("CNY");
        order.setProvider("MOCK");
        order.setStatus(PaymentStatus.PAID);
        order.setPaidAt(now);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        if (paymentOrderMapper.insert(order) != 1) {
            throw new IllegalStateException("创建支付订单失败");
        }
        if (paymentOrderMapper.incrementCredits(userId, plan.getCredits()) != 1) {
            throw new IllegalStateException("增加用户额度失败");
        }

        return toResultVO(order, Math.addExact(creditsBeforePayment, plan.getCredits()));
    }

    private PaymentPlanVO toPlanVO(PaymentPlanEntity plan) {
        return new PaymentPlanVO(
                plan.getId(),
                plan.getName(),
                plan.getCredits(),
                plan.getPriceFen(),
                plan.getDescription(),
                plan.isRecommended()
        );
    }

    private PaymentResultVO toResultVO(PaymentOrderEntity order, int creditsRemaining) {
        return new PaymentResultVO(
                order.getId(),
                order.getStatus(),
                order.getCreditsAdded(),
                creditsRemaining,
                order.getPaidAt()
        );
    }

    private String normalizePlanId(String planId) {
        if (planId == null || planId.isBlank()) {
            throw new PlanNotFoundException();
        }
        return planId.trim();
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128) {
            throw new InvalidIdempotencyKeyException();
        }
        return idempotencyKey;
    }
}
