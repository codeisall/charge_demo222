package com.pdsu.charge_palteform.entity.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CouponStatsResponse {
    private Integer totalCount; // 总优惠券数
    private Integer availableCount; // 可用优惠券数
    private Integer usedCount; // 已使用优惠券数
    private Integer expiredCount; // 已过期优惠券数
    private Integer expiringSoonCount; // 即将过期优惠券数（3天内）
    private BigDecimal totalSavedAmount; // 累计节省金额
    private BigDecimal maxAvailableDeduction; // 当前最大可抵扣金额
}
