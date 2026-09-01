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
import com.learn.paymentservice.vo.MockPaymentVO;
import com.learn.paymentservice.vo.PaymentPlanVO;
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
    void createsPendingOrderWithoutAddingCredits() {
        PaymentPlanEntity plan = plan("starter", 10, 800, true);
        when(paymentOrderMapper.selectActiveCreditsForUpdate(USER_ID)).thenReturn(3);
        when(paymentPlanMapper.selectActiveById("starter")).thenReturn(plan);
        when(paymentOrderMapper.insert(org.mockito.ArgumentMatchers.any())).thenReturn(1);

        MockPaymentVO result = service.createMockPayment(" starter ", "request-1");

        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.credits()).isEqualTo(10);
        assertThat(result.amountFen()).isEqualTo(800);
        assertThat(result.createdAt()).isNotNull();

        ArgumentCaptor<PaymentOrderEntity> orderCaptor =
                ArgumentCaptor.forClass(PaymentOrderEntity.class);
        verify(paymentOrderMapper).insert(orderCaptor.capture());
        PaymentOrderEntity order = orderCaptor.getValue();
        assertThat(order.getUserId()).isEqualTo(USER_ID);
        assertThat(order.getPlanId()).isEqualTo("starter");
        assertThat(order.getIdempotencyKey()).isEqualTo("request-1");
        assertThat(order.getAmountFen()).isEqualTo(800);
        assertThat(order.getProvider()).isEqualTo("MOCK");
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getPaidAt()).isNull();

        InOrder writeOrder = inOrder(paymentOrderMapper, paymentPlanMapper);
        writeOrder.verify(paymentOrderMapper).selectActiveCreditsForUpdate(USER_ID);
        writeOrder.verify(paymentOrderMapper)
                .selectByUserIdAndIdempotencyKey(USER_ID, "request-1");
        writeOrder.verify(paymentPlanMapper).selectActiveById("starter");
        writeOrder.verify(paymentOrderMapper).insert(order);
        verify(paymentOrderMapper, never()).incrementCredits(USER_ID, 10);
    }

    @Test
    void retryReturnsExistingOrderWithoutAddingCreditsAgain() {
        PaymentOrderEntity existing = paidOrder("starter", "request-1", 10);
        when(paymentOrderMapper.selectActiveCreditsForUpdate(USER_ID)).thenReturn(7);
        when(paymentOrderMapper.selectByUserIdAndIdempotencyKey(USER_ID, "request-1"))
                .thenReturn(existing);

        MockPaymentVO result = service.createMockPayment("starter", "request-1");

        assertThat(result.orderId()).isEqualTo(existing.getId());
        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
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

        assertThatThrownBy(() -> service.createMockPayment("starter", "request-1"))
                .isInstanceOf(PaymentIdempotencyConflictException.class);

        verify(paymentOrderMapper, never()).insert(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(paymentPlanMapper);
    }

    @Test
    void rejectsMissingUserBeforeLookingUpPlan() {
        when(paymentOrderMapper.selectActiveCreditsForUpdate(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.createMockPayment("starter", "request-1"))
                .isInstanceOf(PaymentUserUnavailableException.class);

        verifyNoInteractions(paymentPlanMapper);
    }

    @Test
    void rejectsInactiveOrUnknownPlan() {
        when(paymentOrderMapper.selectActiveCreditsForUpdate(USER_ID)).thenReturn(3);

        assertThatThrownBy(() -> service.createMockPayment("missing", "request-1"))
                .isInstanceOf(PlanNotFoundException.class);

        verify(paymentOrderMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsInvalidIdempotencyKeyBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.createMockPayment("starter", " "))
                .isInstanceOf(InvalidIdempotencyKeyException.class);

        verifyNoInteractions(paymentPlanMapper, paymentOrderMapper);
    }

    @Test
    void completesPendingOrderAndAddsCredits() {
        UUID orderId = UUID.randomUUID();
        PaymentOrderEntity order = pendingOrder(orderId, "starter", "request-1", 10);
        when(paymentOrderMapper.selectByIdForUpdate(orderId)).thenReturn(order);
        when(paymentOrderMapper.selectActiveCreditsForUpdate(USER_ID)).thenReturn(3);
        when(paymentOrderMapper.incrementCredits(USER_ID, 10)).thenReturn(1);
        when(paymentOrderMapper.markPaid(
                org.mockito.ArgumentMatchers.eq(orderId),
                org.mockito.ArgumentMatchers.any(Instant.class)
        )).thenReturn(1);

        service.completeMockPayment(orderId);

        InOrder writeOrder = inOrder(paymentOrderMapper);
        writeOrder.verify(paymentOrderMapper).selectByIdForUpdate(orderId);
        writeOrder.verify(paymentOrderMapper).selectActiveCreditsForUpdate(USER_ID);
        writeOrder.verify(paymentOrderMapper).incrementCredits(USER_ID, 10);
        writeOrder.verify(paymentOrderMapper).markPaid(
                org.mockito.ArgumentMatchers.eq(orderId),
                org.mockito.ArgumentMatchers.any(Instant.class)
        );
        verify(paymentOrderMapper, never()).markFailed(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void duplicateMessageDoesNotAddCreditsAgain() {
        UUID orderId = UUID.randomUUID();
        when(paymentOrderMapper.selectByIdForUpdate(orderId))
                .thenReturn(paidOrder("starter", "request-1", 10));

        service.completeMockPayment(orderId);

        verify(paymentOrderMapper, never()).incrementCredits(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt()
        );
        verify(paymentOrderMapper, never()).markPaid(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void marksOrderFailedWhenUserIsUnavailableDuringConsumption() {
        UUID orderId = UUID.randomUUID();
        PaymentOrderEntity order = pendingOrder(orderId, "starter", "request-1", 10);
        when(paymentOrderMapper.selectByIdForUpdate(orderId)).thenReturn(order);
        when(paymentOrderMapper.selectActiveCreditsForUpdate(USER_ID)).thenReturn(null);
        when(paymentOrderMapper.markFailed(
                orderId,
                "CURRENT_USER_UNAVAILABLE",
                "当前用户不可用"
        )).thenReturn(1);

        service.completeMockPayment(orderId);

        verify(paymentOrderMapper).markFailed(
                orderId,
                "CURRENT_USER_UNAVAILABLE",
                "当前用户不可用"
        );
        verify(paymentOrderMapper, never()).incrementCredits(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt()
        );
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
        PaymentOrderEntity order = pendingOrder(UUID.randomUUID(), planId, key, creditsAdded);
        order.setStatus(PaymentStatus.PAID);
        order.setPaidAt(Instant.parse("2026-08-22T02:10:00Z"));
        return order;
    }

    private PaymentOrderEntity pendingOrder(
            UUID orderId,
            String planId,
            String key,
            int creditsAdded
    ) {
        PaymentOrderEntity order = new PaymentOrderEntity();
        order.setId(orderId);
        order.setUserId(USER_ID);
        order.setPlanId(planId);
        order.setIdempotencyKey(key);
        order.setPlanName(planId);
        order.setCreditsAdded(creditsAdded);
        order.setAmountFen(800);
        order.setCurrency("CNY");
        order.setStatus(PaymentStatus.PENDING);
        order.setCreatedAt(Instant.parse("2026-08-22T02:00:00Z"));
        return order;
    }
}
