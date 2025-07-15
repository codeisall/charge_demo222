package com.pdsu.charge_palteform.entity.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ChargeStatusResponse {
    private String orderNo;
    private String connectorId;
    private String stationName;
    private String connectorName;
    private Integer orderStatus; // 订单状态
    private String orderStatusText;
    private Integer chargeStatus; // 充电状态
    private String chargeStatusText;

    // 充电过程数据
    private LocalDateTime startTime;
    private LocalDateTime currentTime;
    private Integer chargeDuration; // 充电时长（分钟）
    private BigDecimal currentPower; // 当前功率
    private BigDecimal totalPower; // 累计充电量
    private BigDecimal soc; // 电池电量百分比

    // 费用信息
    private BigDecimal electricityFee; // 累计电费
    private BigDecimal serviceFee; // 累计服务费
    private BigDecimal totalFee; // 累计总费用

    // 电压电流信息
    private BigDecimal voltageA;
    private BigDecimal currentA;

    // 费率信息
    private BigDecimal electricityPrice; // 当前电价
    private BigDecimal servicePrice; // 当前服务费价格
}
