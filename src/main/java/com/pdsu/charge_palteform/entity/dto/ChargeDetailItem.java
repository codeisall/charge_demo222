package com.pdsu.charge_palteform.entity.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ChargeDetailItem {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal electricityPrice; // 时段电价
    private BigDecimal servicePrice; // 时段服务费价格
    private BigDecimal power; // 时段充电量
    private BigDecimal electricityFee; // 时段电费
    private BigDecimal serviceFee; // 时段服务费
}
