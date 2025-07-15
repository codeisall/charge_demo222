package com.pdsu.charge_palteform.entity.platefrom.charge;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ChargeStatusRequest {
    @JsonProperty("StartChargeSeq")
    private String StartChargeSeq; // 充电订单号
}
