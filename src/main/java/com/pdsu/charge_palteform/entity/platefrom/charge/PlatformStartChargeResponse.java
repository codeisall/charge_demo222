package com.pdsu.charge_palteform.entity.platefrom.charge;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PlatformStartChargeResponse {
    @JsonProperty("StartChargeSeq")
    private String StartChargeSeq; // 充电订单号

    @JsonProperty("StartChargeSeqStat")
    private Integer StartChargeSeqStat; // 充电订单状态：1启动中，2充电中，3停止中，4已结束，5未知

    @JsonProperty("ConnectorID")
    private String ConnectorID;

    @JsonProperty("SuccStat")
    private Integer SuccStat; // 0:成功；1:失败

    @JsonProperty("FailReason")
    private Integer FailReason; // 失败原因
}
