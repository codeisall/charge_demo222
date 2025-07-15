package com.pdsu.charge_palteform.entity.platefrom.station;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ConnectorInfo {

    @JsonProperty("ConnectorID")
    private String ConnectorID;


    private String ConnectorName;

    @JsonProperty("ConnectorType")
    private Integer ConnectorType;

    @JsonProperty("VoltageUpperLimits")
    private Integer VoltageUpperLimits;

    @JsonProperty("VoltageLowerLimits")
    private Integer VoltageLowerLimits;

    @JsonProperty("Current")
    private Integer Current;

    @JsonProperty("Power")
    private BigDecimal Power;

    private String ParkNo;

    @JsonProperty("NationalStandard")
    private Integer NationalStandard;

}
