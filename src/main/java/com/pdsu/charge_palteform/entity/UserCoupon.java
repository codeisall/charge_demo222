package com.pdsu.charge_palteform.entity;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_coupons")
public class UserCoupon {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long couponTemplateId;

    private String couponCode;

    private Integer status; // 1-未使用，2-已使用，3-已过期

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime receiveTime;

    private LocalDateTime validStart;

    private LocalDateTime validEnd;

    private LocalDateTime useTime;

    private String orderNo;
}
