package com.learn.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 本地或演示环境发起模拟支付的请求。 */
public record MockPaymentDTO(
        @NotBlank(message = "套餐 ID 不能为空")
        @Size(max = 50, message = "套餐 ID 不能超过 50 个字符")
        String planId
) {
}
