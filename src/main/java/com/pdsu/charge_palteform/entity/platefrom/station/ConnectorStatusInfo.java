package com.pdsu.charge_palteform.entity.platefrom.station;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ConnectorStatusInfo {

    @JsonProperty("ConnectorID")
    private String ConnectorID;

    @JsonProperty("Status")
    private Integer Status;

    @JsonProperty("ParkStatus")
    private Integer ParkStatus;

    @JsonProperty("LockStatus")
    private Integer LockStatus;
}
