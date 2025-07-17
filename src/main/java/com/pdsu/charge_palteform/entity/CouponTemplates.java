package com.pdsu.charge_palteform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("coupon_templates")
public class CouponTemplates {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Integer type; // 1-满减券，2-折扣券，3-现金券

    private BigDecimal value; // 优惠值

    private BigDecimal minChargeAmount; // 最低消费金额

    private Integer applicableStationType; // 适用站点类型

    private Integer validityType; // 有效期类型：1-固定日期，2-领取后生效

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer validDays;

    private Integer totalQuantity;

    private Integer remainingQuantity;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
