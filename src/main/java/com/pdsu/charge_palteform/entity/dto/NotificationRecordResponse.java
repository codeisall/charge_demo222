package com.pdsu.charge_palteform.entity.dto;


import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationRecordResponse {
    private Long id;
    private String type;
    private String typeText;
    private String title;
    private String content;
    private String relateOrderNo;
    private Integer status;
    private String statusText;
    private LocalDateTime sendTime;
    private LocalDateTime createTime;
}
