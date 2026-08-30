package com.learn.paymentservice.controller;

import com.learn.common.vo.ApiResponse;
import com.learn.paymentservice.dto.MockPaymentDTO;
import com.learn.paymentservice.service.PaymentService;
import com.learn.paymentservice.vo.PaymentResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅在明确开启开关时注册的本地模拟支付入口。 */
@Tag(name = "支付", description = "支付套餐与额度购买")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/payments")
@ConditionalOnProperty(name = "payment.mock.enabled", havingValue = "true")
public class MockPaymentController {

    private final PaymentService paymentService;

    @PostMapping("/mock")
    @Operation(summary = "模拟支付并增加额度（仅限本地或演示环境）")
    public ApiResponse<PaymentResultVO> mockPayment(
            @Parameter(description = "本次支付的唯一幂等键", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody MockPaymentDTO request
    ) {
        return ApiResponse.success(paymentService.mockPayment(request.planId(), idempotencyKey));
    }
}
