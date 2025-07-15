package com.pdsu.charge_palteform.entity.platefrom.charge;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ChargePolicyResponse {
    @JsonProperty("EquipBizSeq")
    private String EquipBizSeq;

    @JsonProperty("ConnectorID")
    private String ConnectorID;

    @JsonProperty("SuccStat")
    private Integer SuccStat;

    @JsonProperty("FailReason")
    private Integer FailReason;

    @JsonProperty("SumPeriod")
    private Integer SumPeriod; // 时段数

    @JsonProperty("PolicyInfos")
    private List<PolicyInfo> PolicyInfos; // 策略信息列表
}
