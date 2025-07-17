package com.pdsu.charge_palteform.entity.platefrom.station;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class EquipmentInfo {

    @JsonProperty("EquipmentID")
    private String EquipmentID;

    private String ManufacturerID;
    private String EquipmentModel;
    private String ProductionDate;

    @JsonProperty("EquipmentType")
    private Integer EquipmentType;

    @JsonProperty("ConnectorInfos")
    private List<ConnectorInfo> ConnectorInfos;

    private BigDecimal EquipmentLng;
    private BigDecimal EquipmentLat;

    @JsonProperty("Power")
    private BigDecimal Power;

    private String EquipmentName;
}
