package com.pdsu.charge_palteform.entity.platefrom.station;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class StationInfo {

    @JsonProperty("StationID")
    private String StationID;

    @JsonProperty("OperatorID")
    private String OperatorID;

    @JsonProperty("EquipmentOwnerID")
    private String EquipmentOwnerID;

    @JsonProperty("StationName")
    private String StationName;

    @JsonProperty("CountryCode")
    private String CountryCode;

    @JsonProperty("AreaCode")
    private String AreaCode;

    @JsonProperty("Address")
    private String Address;

    @JsonProperty("StationTel")
    private String StationTel;

    @JsonProperty("ServiceTel")
    private String ServiceTel;


    private Integer StationType;

    @JsonProperty("StationStatus")
    private Integer StationStatus;
    private Integer ParkNums;

    @JsonProperty("StationLng")
    private BigDecimal StationLng;
    @JsonProperty("StationLat")
    private BigDecimal StationLat;

    private String SiteGuide;
    @JsonProperty("Construction")
    private Integer Construction;
    private List<String> Pictures;
    private String MatchCars;
    private String ParkInfo;
    private String BusineHours;
    private String ElectricityFee;
    private String ServiceFee;

    @JsonProperty("ParkFee")
    private String ParkFee;
    private Integer ParkDiscountType;
    private String Payment;
    private Integer SupportOrder;
    private String Remark;

    @JsonProperty("EquipmentInfos")
    private List<EquipmentInfo> EquipmentInfos;
}
