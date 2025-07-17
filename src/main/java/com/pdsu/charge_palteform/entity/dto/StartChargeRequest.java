package com.pdsu.charge_palteform.entity.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StartChargeRequest {
    @NotBlank(message = "充电桩ID不能为空")
    private String connectorId;

    private String qrCode; // 二维码信息（可选）

    private Long couponId;

    // 新增充电控制参数
    private Integer chargeDuration; // 充电时长（分钟）
    private BigDecimal targetSoc; // 目标电量百分比
    private BigDecimal targetAmount; // 目标金额
    private Integer stopCondition; // 停止条件：1-时间，2-电量，3-金额

}
