package com.pdsu.charge_palteform.entity.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserCouponListResponse {
    private Long couponId;
    private String couponCode;
    private String name;
    private String description;
    private Integer type; // 1-满减券，2-折扣券，3-现金券
    private String typeText;
    private BigDecimal value;
    private BigDecimal minChargeAmount;
    private Integer status; // 1-未使用，2-已使用，3-已过期
    private String statusText;
    private LocalDateTime validStart;
    private LocalDateTime validEnd;
    private LocalDateTime receiveTime;
    private LocalDateTime useTime;
    private String orderNo; // 使用的订单号
    private boolean isExpiringSoon; // 是否即将过期（3天内）
}
