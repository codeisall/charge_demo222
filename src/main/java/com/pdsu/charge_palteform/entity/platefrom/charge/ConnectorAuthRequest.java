package com.pdsu.charge_palteform.entity.platefrom.charge;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

// 设备认证请求
@Data
public class ConnectorAuthRequest {
    @JsonProperty("EquipAuthSeq")
    private String EquipAuthSeq; // 设备认证流水号

    @JsonProperty("ConnectorID")
    private String ConnectorID; // 充电设备接口编码
}
