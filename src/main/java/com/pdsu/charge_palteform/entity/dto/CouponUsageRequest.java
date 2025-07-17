package com.pdsu.charge_palteform.entity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CouponUsageRequest {
    @NotNull(message = "优惠券ID不能为空")
    private Long couponId;

    @NotNull(message = "订单金额不能为空")
    private BigDecimal orderAmount;

    private String connectorId; // 充电桩ID，用于验证适用范围
}
