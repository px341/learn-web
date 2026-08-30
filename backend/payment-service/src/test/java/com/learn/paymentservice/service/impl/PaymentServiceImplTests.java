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
import com.learn.paymentservice.vo.PaymentPlanVO;
import com.learn.paymentservice.vo.PaymentResultVO;
import com.learn.security.currentuser.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTests {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private PaymentPlanMapper paymentPlanMapper;

    @Mock
    private PaymentOrderMapper paymentOrderMapper;

    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        CurrentUserProvider currentUserProvider = () -> USER_ID;
        service = new PaymentServiceImpl(
                currentUserProvider,
                paymentPlanMapper,
                paymentOrderMapper
        );
    }

    @Test
    void returnsActivePlansInMapperOrder() {
        when(paymentPlanMapper.selectActivePlans()).thenReturn(List.of(
                plan("trial", 1, 100, false),
                plan("starter", 10, 800, true)
        ));

        List<PaymentPlanVO> result = service.getPlans();

        assertThat(result).extracting(PaymentPlanVO::id)
                .containsExactly("trial", "starter");
        assertThat(result.get(1).credits()).isEqualTo(10);
        assertThat(result.get(1).recommended()).isTrue();
    }

    @Test
    void createsPaidOrderAndAddsCreditsExactlyOnce() {
        PaymentPlanEntity plan = plan("starter", 10, 800, true);
        when(paymentOrderMapper.selectActiveCreditsForUpdate(USER_ID)).thenReturn(3);
        when(paymentPlanMapper.selectActiveById("starter")).thenReturn(plan);
        when(paymentOrderMapper.insert(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(paymentOrderMapper.incrementCredits(USER_ID, 10)).thenReturn(1);

        PaymentResultVO result = service.mockPayment(" starter ", "request-1");

        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.creditsAdded()).isEqualTo(10);
        assertThat(result.creditsRemaining()).isEqualTo(13);
        assertThat(result.paidAt()).isNotNull();

        ArgumentCaptor<PaymentOrderEntity> orderCaptor =
                ArgumentCaptor.forClass(PaymentOrderEntity.class);
        verify(paymentOrderMapper).insert(orderCaptor.capture());
        PaymentOrderEntity order = orderCaptor.getValue();
        assertThat(order.getUserId()).isEqualTo(USER_ID);
        assertThat(order.getPlanId()).isEqualTo("starter");
        assertThat(order.getIdempotencyKey()).isEqualTo("request-1");
        assertThat(order.getAmountFen()).isEqualTo(800);
        assertThat(order.getProvider()).isEqualTo("MOCK");
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PAID);

        InOrder writeOrder = inOrder(paymentOrderMapper, paymentPlanMapper);
        writeOrder.verify(paymentOrderMapper).selectActiveCreditsForUpdate(USER_ID);
        writeOrder.verify(paymentOrderMapper)
                .selectByUserIdAndIdempotencyKey(USER_ID, "request-1");
        writeOrder.verify(paymentPlanMapper).selectActiveById("starter");
        writeOrder.verify(paymentOrderMapper).insert(order);
        writeOrder.verify(paymentOrderMapper).incrementCredits(USER_ID, 10);
    }

    @Test
    void retryReturnsExistingOrderWithoutAddingCreditsAgain() {
        PaymentOrderEntity existing = paidOrder("starter", "request-1", 10);
        when(paymentOrderMapper.selectActiveCreditsForUpdate(USER_ID)).thenReturn(7);
        when(paymentOrderMapper.selectByUserIdAndIdempotencyKey(USER_ID, "request-1"))
                .thenReturn(existing);

        PaymentResultVO result = service.mockPayment("starter", "request-1");

        assertThat(result.orderId()).isEqualTo(existing.getId());
        assertThat(result.creditsRemaining()).isEqualTo(7);
        verify(paymentOrderMapper, never()).insert(org.mockito.ArgumentMatchers.any());
        verify(paymentOrderMapper, never())
                .incrementCredits(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verifyNoInteractions(paymentPlanMapper);
    }

    @Test
    void rejectsReusingKeyForAnotherPlan() {
        PaymentOrderEntity existing = paidOrder("trial", "request-1", 1);
        when(paymentOrderMapper.selectActiveCreditsForUpdate(USER_ID)).thenReturn(4);
        when(paymentOrderMapper.selectByUserIdAndIdempotencyKey(USER_ID, "request-1"))
                .thenReturn(existing);

        assertThatThrownBy(() -> service.mockPayment("starter", "request-1"))
                .isInstanceOf(PaymentIdempotencyConflictException.class);

        verify(paymentOrderMapper, never()).insert(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(paymentPlanMapper);
    }

    @Test
    void rejectsMissingUserBeforeLookingUpPlan() {
        when(paymentOrderMapper.selectActiveCreditsForUpdate(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.mockPayment("starter", "request-1"))
                .isInstanceOf(PaymentUserUnavailableException.class);

        verifyNoInteractions(paymentPlanMapper);
    }

    @Test
    void rejectsInactiveOrUnknownPlan() {
        when(paymentOrderMapper.selectActiveCreditsForUpdate(USER_ID)).thenReturn(3);

        assertThatThrownBy(() -> service.mockPayment("missing", "request-1"))
                .isInstanceOf(PlanNotFoundException.class);

        verify(paymentOrderMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsInvalidIdempotencyKeyBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.mockPayment("starter", " "))
                .isInstanceOf(InvalidIdempotencyKeyException.class);

        verifyNoInteractions(paymentPlanMapper, paymentOrderMapper);
    }

    private PaymentPlanEntity plan(
            String id,
            int credits,
            int priceFen,
            boolean recommended
    ) {
        PaymentPlanEntity plan = new PaymentPlanEntity();
        plan.setId(id);
        plan.setName(id);
        plan.setCredits(credits);
        plan.setPriceFen(priceFen);
        plan.setDescription("description");
        plan.setRecommended(recommended);
        plan.setStatus("ACTIVE");
        return plan;
    }

    private PaymentOrderEntity paidOrder(String planId, String key, int creditsAdded) {
        PaymentOrderEntity order = new PaymentOrderEntity();
        order.setId(UUID.randomUUID());
        order.setUserId(USER_ID);
        order.setPlanId(planId);
        order.setIdempotencyKey(key);
        order.setCreditsAdded(creditsAdded);
        order.setStatus(PaymentStatus.PAID);
        order.setPaidAt(Instant.parse("2026-08-22T02:10:00Z"));
        return order;
    }
}
