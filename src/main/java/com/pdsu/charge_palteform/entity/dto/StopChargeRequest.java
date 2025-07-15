package com.pdsu.charge_palteform.entity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StopChargeRequest {
    @NotBlank(message = "订单号不能为空")
    private String orderNo;
}
