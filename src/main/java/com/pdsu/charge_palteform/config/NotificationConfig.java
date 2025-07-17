package com.pdsu.charge_palteform.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "notification")
public class NotificationConfig {

    private Wechat wechat = new Wechat();

    @Data
    public static class Wechat {
        private boolean enabled = true;
        private int retryTimes = 3;
        private long templateCacheExpire = 3600; // 模板缓存时间(秒)

        // 模板ID配置
        private Map<String, String> templates;

        public String getTemplateId(String type) {
            return templates != null ? templates.get(type) : null;
        }
    }
}
