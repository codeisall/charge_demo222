package com.pdsu.charge_palteform.entity.platefrom.charge;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ConnectorAuthResponse {
    @JsonProperty("EquipAuthSeq")
    private String EquipAuthSeq;

    @JsonProperty("ConnectorID")
    private String ConnectorID;

    @JsonProperty("SuccStat")
    private Integer SuccStat; // 0:成功；1:失败

    @JsonProperty("FailReason")
    private Integer FailReason; // 失败原因
}
