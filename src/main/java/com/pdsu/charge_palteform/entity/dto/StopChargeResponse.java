package com.pdsu.charge_palteform.entity.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StopChargeResponse {

    private String orderNo;
    private Integer chargeStatus;
    private String statusText;
    private LocalDateTime endTime;
    private String message;

}
