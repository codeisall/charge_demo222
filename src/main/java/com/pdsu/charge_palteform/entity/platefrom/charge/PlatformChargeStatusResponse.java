package com.pdsu.charge_palteform.entity.platefrom.charge;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PlatformChargeStatusResponse {
    @JsonProperty("StartChargeSeq")
    private String StartChargeSeq;

    @JsonProperty("StartChargeSeqStat")
    private Integer StartChargeSeqStat; // 充电订单状态

    @JsonProperty("ConnectorID")
    private String ConnectorID;

    @JsonProperty("ConnectorStatus")
    private Integer ConnectorStatus; // 充电设备接口状态

    @JsonProperty("CurrentA")
    private BigDecimal CurrentA; // A相电流

    @JsonProperty("CurrentB")
    private BigDecimal CurrentB; // B相电流

    @JsonProperty("CurrentC")
    private BigDecimal CurrentC; // C相电流

    @JsonProperty("VoltageA")
    private BigDecimal VoltageA; // A相电压

    @JsonProperty("VoltageB")
    private BigDecimal VoltageB; // B相电压

    @JsonProperty("VoltageC")
    private BigDecimal VoltageC; // C相电压

    @JsonProperty("Soc")
    private BigDecimal Soc; // 电池剩余电量

    @JsonProperty("StartTime")
    private String StartTime; // 开始充电时间

    @JsonProperty("EndTime")
    private String EndTime; // 本次采样时间

    @JsonProperty("TotalPower")
    private BigDecimal TotalPower; // 累计充电量

    @JsonProperty("ElecMoney")
    private BigDecimal ElecMoney; // 累计电费

    @JsonProperty("SeviceMoney")
    private BigDecimal SeviceMoney; // 累计服务费

    @JsonProperty("TotalMoney")
    private BigDecimal TotalMoney; // 累计总金额

    @JsonProperty("SumPeriod")
    private Integer SumPeriod; // 时段数

    @JsonProperty("ChargeDetails")
    private List<ChargeDetail> ChargeDetails; // 充电明细信息
}
