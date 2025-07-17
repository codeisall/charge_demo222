package com.pdsu.charge_palteform.entity.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StartChargeRequest {
    @NotBlank(message = "充电桩ID不能为空")
    private String connectorId;

    private String qrCode; // 二维码信息（可选）

    private Long couponId;

}
