package com.pdsu.charge_palteform.entity.platefrom.charge;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PlatformStartChargeRequest {
    @JsonProperty("StartChargeSeq")
    private String StartChargeSeq; // 充电订单号

    @JsonProperty("ConnectorID")
    private String ConnectorID; // 充电设备接口编码

    @JsonProperty("QRCode")
    private String QRCode; // 二维码其它信息（可选）
}
