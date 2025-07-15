package com.pdsu.charge_palteform.entity.platefrom.charge;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ChargeDetail {
    @JsonProperty("DetailStartTime")
    private String DetailStartTime; // 开始时间

    @JsonProperty("DetailEndTime")
    private String DetailEndTime; // 结束时间

    @JsonProperty("ElecPrice")
    private BigDecimal ElecPrice; // 时段电价

    @JsonProperty("SevicePrice")
    private BigDecimal SevicePrice; // 时段服务费价格

    @JsonProperty("DetailPower")
    private BigDecimal DetailPower; // 时段充电量

    @JsonProperty("DetailElecMoney")
    private BigDecimal DetailElecMoney; // 时段电费

    @JsonProperty("DetailSeviceMoney")
    private BigDecimal DetailSeviceMoney; // 时段服务费
}
