package com.pdsu.charge_palteform.entity.platefrom.charge;


import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ChargeStatusData {
    private String platformOrderNo; // 平台订单号
    private String connectorId;
    private Integer chargeStatus; // 充电状态
    private Integer connectorStatus; // 设备状态

    // 电气参数
    private BigDecimal currentA;
    private BigDecimal currentB;
    private BigDecimal currentC;
    private BigDecimal voltageA;
    private BigDecimal voltageB;
    private BigDecimal voltageC;
    private BigDecimal soc; // 电池电量

    // 时间信息
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // 费用信息
    private BigDecimal totalPower; // 累计充电量
    private BigDecimal electricityFee; // 累计电费
    private BigDecimal serviceFee; // 累计服务费
    private BigDecimal totalFee; // 累计总费用

    // 充电明细
    private List<ChargeDetailData> chargeDetails;

    @Data
    public static class ChargeDetailData {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private BigDecimal electricityPrice;
        private BigDecimal servicePrice;
        private BigDecimal power;
        private BigDecimal electricityFee;
        private BigDecimal serviceFee;
    }
}
