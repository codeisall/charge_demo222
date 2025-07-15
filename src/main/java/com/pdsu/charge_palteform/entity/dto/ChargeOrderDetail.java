package com.pdsu.charge_palteform.entity.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ChargeOrderDetail {
    private String orderNo;
    private String stationId;
    private String stationName;
    private String stationAddress;
    private String connectorId;
    private String connectorName;
    private Integer connectorType;
    private String connectorTypeText;

    private Integer status;
    private String statusText;
    private Integer chargeStatus;
    private String chargeStatusText;
    private Integer stopReason;
    private String stopReasonText;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer chargeDuration; // 充电时长（分钟）

    private BigDecimal totalPower; // 总充电量
    private BigDecimal electricityFee; // 电费
    private BigDecimal serviceFee; // 服务费
    private BigDecimal totalFee; // 总费用

    // 充电明细（分时段）
    private List<ChargeDetailItem> chargeDetails;
}
