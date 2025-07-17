package com.pdsu.charge_palteform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdsu.charge_palteform.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Component
public class ChargingWebSocketHandler implements WebSocketHandler {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    private static final Map<Long, WebSocketSession> USER_SESSIONS = new ConcurrentHashMap<>();

    private static final Map<String, WebSocketSession> ORDER_SESSIONS = new ConcurrentHashMap<>();


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = getTokenFromSession(session);
        if (token == null || !jwtUtil.validateToken(token)) {
            log.warn("WebSocket连接认证失败，关闭连接");
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("认证失败"));
            return;
        }
        Long userId = jwtUtil.getUserIdFromToken(token);
        USER_SESSIONS.put(userId, session);
        session.getAttributes().put("userId", userId);
        log.info("用户{}WebSocket连接建立成功", userId);
        // 发送连接成功消息
        sendMessage(session, Map.of(
                "type", "connection",
                "status", "success",
                "message", "连接成功"
        ));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String payload = message.getPayload().toString();
        log.debug("收到WebSocket消息: {}", payload);
        try {
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
            String type = (String) msg.get("type");
            switch (type) {
                case "subscribe_order":
                    // 订阅订单状态
                    String orderNo = (String) msg.get("orderNo");
                    if (orderNo != null) {
                        ORDER_SESSIONS.put(orderNo, session);
                        session.getAttributes().put("subscribedOrder", orderNo);
                        log.info("用户订阅订单: {}", orderNo);
                    }
                    break;
                case "unsubscribe_order":
                    // 取消订阅订单
                    String unsubOrderNo = (String) msg.get("orderNo");
                    if (unsubOrderNo != null) {
                        ORDER_SESSIONS.remove(unsubOrderNo);
                        session.getAttributes().remove("subscribedOrder");
                        log.info("用户取消订阅订单: {}", unsubOrderNo);
                    }
                    break;
                case "ping":
                    // 心跳检测
                    sendMessage(session, Map.of("type", "pong"));
                    break;
                default:
                    log.warn("未知消息类型: {}", type);
            }
        } catch (Exception e) {
            log.error("处理WebSocket消息异常", e);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket传输异常", exception);
        removeSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        removeSession(session);
        log.info("WebSocket连接关闭: {}", closeStatus);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    public static void sendToUser(Long userId, Object message) {
        WebSocketSession session = USER_SESSIONS.get(userId);
        if (session != null && session.isOpen()) {
            sendMessage(session, message);
        }
    }

    public static void sendToOrder(String orderNo, Object message) {
        WebSocketSession session = ORDER_SESSIONS.get(orderNo);
        if (session != null && session.isOpen()) {
            sendMessage(session, message);
        }
    }

    public static void broadcast(Object message) {
        USER_SESSIONS.values().forEach(session -> {
            if (session.isOpen()) {
                sendMessage(session, message);
            }
        });
    }

    private static void sendMessage(WebSocketSession session, Object message) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("发送WebSocket消息失败", e);
        }
    }

    private void removeSession(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        String orderNo = (String) session.getAttributes().get("subscribedOrder");

        if (userId != null) {
            USER_SESSIONS.remove(userId);
        }
        if (orderNo != null) {
            ORDER_SESSIONS.remove(orderNo);
        }
    }

    private String getTokenFromSession(WebSocketSession session) {
        // 从查询参数获取token
        String query = session.getUri().getQuery();
        if (query != null && query.contains("token=")) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("token=")) {
                    return param.substring(6);
                }
            }
        }
        return null;
    }

    /**
     * 获取在线用户数量
     */
    public static int getOnlineUserCount() {
        return USER_SESSIONS.size();
    }


}
