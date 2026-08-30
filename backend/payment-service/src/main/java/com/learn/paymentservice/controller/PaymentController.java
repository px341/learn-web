package com.learn.paymentservice.controller;

import com.learn.common.vo.ApiResponse;
import com.learn.paymentservice.service.PaymentService;
import com.learn.paymentservice.vo.PaymentPlanVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 查询服务端维护的支付套餐。 */
@Tag(name = "支付", description = "支付套餐与额度购买")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/plans")
    @Operation(summary = "返回启用中的支付套餐")
    public ApiResponse<List<PaymentPlanVO>> getPlans() {
        return ApiResponse.success(paymentService.getPlans());
    }
}
