package com.pdsu.charge_palteform.entity;

import lombok.Data;

import java.util.Map;

@Data
public class NotificationTemplate {
    private String templateId;
    private String title;
    private String content;
    private Map<String, String> data; // 模板变量数据
    private String page; // 跳转页面
    private String miniProgramState; // 小程序状态：developer, trial, formal
}
