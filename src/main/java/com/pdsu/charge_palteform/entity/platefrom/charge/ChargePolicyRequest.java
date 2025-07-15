package com.pdsu.charge_palteform.entity.platefrom.charge;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ChargePolicyRequest {
    @JsonProperty("EquipBizSeq")
    private String EquipBizSeq; // 业务策略查询流水号

    @JsonProperty("ConnectorID")
    private String ConnectorID; // 充电设备接口编码
}
