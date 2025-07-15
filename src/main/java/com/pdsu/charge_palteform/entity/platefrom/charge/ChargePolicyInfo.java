package com.pdsu.charge_palteform.entity.platefrom.charge;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ChargePolicyInfo {
    private String connectorId;
    private Integer sumPeriod; // 时段数
    private List<PolicyPeriod> periods; // 分时段策略
    private BigDecimal currentElectricityPrice; // 当前电价
    private BigDecimal currentServicePrice; // 当前服务费价格

    @Data
    public static class PolicyPeriod {
        private String startTime; // 开始时间 HHmmss
        private BigDecimal electricityPrice; // 电价
        private BigDecimal servicePrice; // 服务费价格
    }
}
