package com.pdsu.charge_palteform.entity;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification_records")
public class NotificationRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type; // 通知类型：charge_start, charge_complete, charge_fault, payment_success等
    private String title;
    private String content;
    private String templateId; // 微信模板ID
    private String relateOrderNo; // 关联订单号
    private Integer channel; // 推送渠道：1-微信模板消息，2-站内消息

    private Integer status; // 发送状态：1-待发送，2-发送成功，3-发送失败
    private String errorMessage; // 失败原因
    private Integer retryCount; // 重试次数
    private LocalDateTime sendTime; // 发送时间

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
