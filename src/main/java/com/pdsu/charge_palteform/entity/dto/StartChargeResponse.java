package com.pdsu.charge_palteform.entity.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StartChargeResponse {
    private String orderNo;
    private String connectorId;
    private Integer chargeStatus; // 1-启动中，2-充电中，3-停止中，4-已结束，5-未知
    private String statusText;
    private LocalDateTime startTime;
    private String message;
}
