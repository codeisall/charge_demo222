package com.pdsu.charge_palteform.entity.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ChargeOrderListItem {
    private String orderNo;
    private String stationName;
    private String connectorName;
    private Integer status;
    private String statusText;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal totalPower;
    private BigDecimal totalFee;
    private Integer chargeDuration; // 充电时长（分钟）
}
