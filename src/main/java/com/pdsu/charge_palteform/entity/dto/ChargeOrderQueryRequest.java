package com.pdsu.charge_palteform.entity.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChargeOrderQueryRequest {
    private Integer status; // 状态筛选
    private LocalDateTime startDate; // 开始日期
    private LocalDateTime endDate; // 结束日期
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
