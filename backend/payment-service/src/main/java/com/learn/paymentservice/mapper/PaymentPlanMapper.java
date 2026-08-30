package com.learn.paymentservice.mapper;

import com.learn.paymentservice.entity.PaymentPlanEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PaymentPlanMapper {

    List<PaymentPlanEntity> selectActivePlans();

    PaymentPlanEntity selectActiveById(String planId);
}
