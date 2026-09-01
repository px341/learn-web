package com.learn.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 本地或演示环境发起模拟支付的请求。
 *
 * <p>客户端只负责选择套餐。金额、币种和应增加的额度必须由服务端根据
 * {@code planId} 查询并生成快照，不能信任客户端提交的对应数值。</p>
 */
public record MockPaymentDTO(
        @NotBlank(message = "套餐 ID 不能为空")
        @Size(max = 50, message = "套餐 ID 不能超过 50 个字符")
        String planId
) {

    /** 统一清理套餐 ID 两侧的空白字符。 */
    public MockPaymentDTO {
        if (planId != null) {
            planId = planId.trim();
        }
    }
}
