package com.pdsu.charge_palteform.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "notification")
public class NotificationConfig {

    private Realtime realtime = new Realtime();
    private Wechat wechat = new Wechat();

    @Data
    public static class Realtime {
        private boolean enabled = true;
        private int heartbeatInterval = 30; // 心跳间隔（秒）
        private int reconnectAttempts = 3; // 重连尝试次数
        private long connectionTimeout = 10000; // 连接超时（毫秒）
    }

    @Data
    public static class Wechat {
        private boolean enabled = false; // 默认关闭微信通知，使用实时推送
        private int retryTimes = 3;
        private long templateCacheExpire = 3600; // 模板缓存时间(秒)
    }
}
