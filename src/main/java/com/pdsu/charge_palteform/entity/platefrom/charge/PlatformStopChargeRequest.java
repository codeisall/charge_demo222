package com.pdsu.charge_palteform.entity.platefrom.charge;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PlatformStopChargeRequest {
    @JsonProperty("StartChargeSeq")
    private String StartChargeSeq; // 充电订单号

    @JsonProperty("ConnectorID")
    private String ConnectorID; // 充电设备接口编码
}
