package com.pdsu.charge_palteform.config;

import lombok.extern.slf4j.Slf4j;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;

import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        // 创建连接池管理器
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(200)      // 最大连接数
                .setMaxConnPerRoute(50)     // 每个路由的最大连接数
                .build();

        // 添加连接池监控日志
        logConnectionPoolStats(connectionManager);

        // 创建HTTP客户端
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .evictExpiredConnections()  // 自动清理过期连接
                .addRequestInterceptorFirst(new LoggingRequestInterceptor())  // 请求日志
                .addResponseInterceptorFirst(new LoggingResponseInterceptor()) // 响应日志
                .setDefaultRequestConfig(org.apache.hc.client5.http.config.RequestConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(10))    // 连接超时
                        .setResponseTimeout(Timeout.ofSeconds(30))   // 响应超时
                        .build())
                .build();

        // 创建RequestFactory
        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        return new RestTemplate(factory);
    }

    /**
     * 记录连接池状态
     */
    private void logConnectionPoolStats(PoolingHttpClientConnectionManager connectionManager) {
        log.info("HTTP连接池已初始化 - 最大连接数: {}, 每路由最大连接数: {}",
                connectionManager.getMaxTotal(),
                connectionManager.getDefaultMaxPerRoute());

        // 定期记录连接池状态（可选）
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(300000); // 每5分钟记录一次
                    log.debug("HTTP连接池状态 - 总连接数: {}, 可用连接: {}, 待处理请求: {}",
                            connectionManager.getTotalStats().getAvailable(),
                            connectionManager.getTotalStats().getLeased(),
                            connectionManager.getTotalStats().getPending());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "http-connection-pool-monitor").start();
    }

    /**
     * HTTP请求日志拦截器
     */
    private static class LoggingRequestInterceptor implements HttpRequestInterceptor {
        @Override
        public void process(HttpRequest request, EntityDetails entity, HttpContext context) throws HttpException, IOException {
            // 记录开始时间到上下文
            context.setAttribute("startTime", System.nanoTime());

            if (log.isDebugEnabled()) {
                StringBuilder logMsg = new StringBuilder();
                logMsg.append("\n=== HTTP 请求 ===\n");
                logMsg.append(String.format("方法: %s\n", request.getMethod()));
                logMsg.append(String.format("URL: %s\n", request.getRequestUri()));
                logMsg.append(String.format("协议: %s\n", request.getVersion()));

                // 记录请求头
                logMsg.append("请求头:\n");
                for (Header header : request.getHeaders()) {
                    logMsg.append(String.format("  %s: %s\n", header.getName(), header.getValue()));
                }

                log.debug(logMsg.toString());
            } else {
                log.info("发送HTTP请求: {} {}", request.getMethod(), request.getRequestUri());
            }
        }
    }

    /**
     * HTTP响应日志拦截器
     */
    private static class LoggingResponseInterceptor implements HttpResponseInterceptor {
        @Override
        public void process(HttpResponse response, EntityDetails entity, HttpContext context) throws HttpException, IOException {
            // 获取开始时间
            Long startTime = (Long) context.getAttribute("startTime");
            if (startTime == null) {
                return;
            }

            long endTime = System.nanoTime();
            double duration = (endTime - startTime) / 1e6d; // 转换为毫秒

            int statusCode = response.getCode();
            String reasonPhrase = response.getReasonPhrase();

            // 根据状态码确定日志级别
            if (log.isDebugEnabled()) {
                StringBuilder logMsg = new StringBuilder();
                logMsg.append("\n=== HTTP 响应 ===\n");
                logMsg.append(String.format("状态: %d %s\n", statusCode, reasonPhrase));
                logMsg.append(String.format("耗时: %.2fms\n", duration));

                // 记录响应头
                logMsg.append("响应头:\n");
                for (Header header : response.getHeaders()) {
                    logMsg.append(String.format("  %s: %s\n", header.getName(), header.getValue()));
                }

                log.debug(logMsg.toString());
            } else {
                String logLevel = statusCode >= 400 ? "ERROR" : "INFO";

                switch (logLevel) {
                    case "ERROR":
                        log.error("HTTP错误响应: {} {} (耗时: {:.2f}ms)", statusCode, reasonPhrase, duration);
                        break;
                    case "WARN":
                        log.warn("HTTP警告响应: {} {} (耗时: {:.2f}ms)", statusCode, reasonPhrase, duration);
                        break;
                    default:
                        log.info("HTTP成功响应: {} {} (耗时: {:.2f}ms)", statusCode, reasonPhrase, duration);
                }
            }

            // 清理上下文
            context.removeAttribute("startTime");
        }
    }

}
