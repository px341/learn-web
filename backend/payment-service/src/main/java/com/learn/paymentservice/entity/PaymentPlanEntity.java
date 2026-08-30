package com.learn.paymentservice.entity;

import lombok.Data;

import java.time.Instant;

/** payment_plans 表的内部持久化对象。 */
@Data
public class PaymentPlanEntity {
    private String id;
    private String name;
    private Integer credits;
    private Integer priceFen;
    private String description;
    private boolean recommended;
    private String status;
    private Integer sortOrder;
    private Instant createdAt;
    private Instant updatedAt;
}
