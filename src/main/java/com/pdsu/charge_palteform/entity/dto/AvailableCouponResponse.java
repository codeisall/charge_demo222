package com.pdsu.charge_palteform.entity.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
public class AvailableCouponResponse {
    private Long couponId;
    private String couponCode;
    private String name;
    private Integer type;
    private BigDecimal value;
    private BigDecimal minChargeAmount;
    private LocalDateTime validEnd;
    private BigDecimal deductionAmount; // 可抵扣金额
    private boolean canUse; // 是否可使用
}
