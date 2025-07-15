package com.pdsu.charge_palteform.entity.platefrom.charge;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PolicyInfo {
    @JsonProperty("StartTime")
    private String StartTime; // 时段起始时间点，格式"HHmmss"

    @JsonProperty("ElecPrice")
    private String ElecPrice; // 时段电费，小数点后4位

    @JsonProperty("SevicePrice")
    private String SevicePrice; // 时段服务费，小数点后4位

    // 手动添加getter方法以确保正确映射
    public String getStartTime() {
        return StartTime;
    }

    public String getElecPrice() {
        return ElecPrice;
    }

    public String getSevicePrice() {
        return SevicePrice;
    }
}
