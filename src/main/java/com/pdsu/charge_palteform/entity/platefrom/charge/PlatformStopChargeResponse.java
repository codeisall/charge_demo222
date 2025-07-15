package com.pdsu.charge_palteform.entity.platefrom.charge;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PlatformStopChargeResponse {
    @JsonProperty("StartChargeSeq")
    private String StartChargeSeq;

    @JsonProperty("StartChargeSeqStat")
    private Integer StartChargeSeqStat; // 充电订单状态

    @JsonProperty("SuccStat")
    private Integer SuccStat;

    @JsonProperty("FailReason")
    private Integer FailReason;
}
