package com.pdsu.charge_palteform.service.impl;

import com.pdsu.charge_palteform.config.ChargingWebSocketHandler;
import com.pdsu.charge_palteform.entity.ChargeOrder;
import com.pdsu.charge_palteform.entity.ChargingConnector;
import com.pdsu.charge_palteform.entity.ChargingStation;
import com.pdsu.charge_palteform.entity.platefrom.charge.ChargeStatusData;
import com.pdsu.charge_palteform.service.ChargingStationService;
import com.pdsu.charge_palteform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeNotificationServiceImpl implements NotificationService {

    private final ChargingStationService stationService;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Async
    public void sendChargeStartNotification(Long userId, String orderNo, String stationName, String connectorId) {
        log.info("发送充电启动实时通知: userId={}, orderNo={}", userId, orderNo);

        Map<String, Object> message = new HashMap<>();
        message.put("type", "charge_start");
        message.put("orderNo", orderNo);
        message.put("stationName", stationName);
        message.put("connectorId", connectorId);
        message.put("status", "charging");
        message.put("title", "充电启动成功");
        message.put("content", String.format("您在%s的充电已开始", stationName));
        message.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));

        // 发送给指定用户
        ChargingWebSocketHandler.sendToUser(userId, message);

        // 发送给订阅该订单的连接
        ChargingWebSocketHandler.sendToOrder(orderNo, message);
    }

    @Override
    @Async
    public void sendChargeStatusNotification(Long userId, String orderNo, ChargeStatusData statusData) {
        log.info("发送充电状态变化通知: userId={}, orderNo={}, status={}",
                userId, orderNo, statusData.getChargeStatus());

        Map<String, Object> message = new HashMap<>();
        message.put("type", "charge_status");
        message.put("orderNo", orderNo);
        message.put("chargeStatus", statusData.getChargeStatus());
        message.put("chargeStatusText", getChargeStatusText(statusData.getChargeStatus()));
        message.put("connectorStatus", statusData.getConnectorStatus());

        // 电气参数
        if (statusData.getCurrentA() != null) {
            message.put("currentA", statusData.getCurrentA());
        }
        if (statusData.getVoltageA() != null) {
            message.put("voltageA", statusData.getVoltageA());
        }
        if (statusData.getSoc() != null) {
            message.put("soc", statusData.getSoc());
        }

        // 费用信息
        if (statusData.getTotalPower() != null) {
            message.put("totalPower", statusData.getTotalPower());
        }
        if (statusData.getTotalFee() != null) {
            message.put("totalFee", statusData.getTotalFee());
        }

        message.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));

        ChargingWebSocketHandler.sendToUser(userId, message);
        ChargingWebSocketHandler.sendToOrder(orderNo, message);
    }

    @Override
    @Async
    public void sendChargeProgressNotification(Long userId, String orderNo,
                                               BigDecimal soc, BigDecimal totalPower, BigDecimal totalFee, Integer chargeDuration) {
        log.debug("发送充电进度实时通知: userId={}, orderNo={}, soc={}", userId, orderNo, soc);

        Map<String, Object> message = new HashMap<>();
        message.put("type", "charge_progress");
        message.put("orderNo", orderNo);
        message.put("soc", soc);
        message.put("totalPower", totalPower);
        message.put("totalFee", totalFee);
        message.put("chargeDuration", chargeDuration);
        message.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));

        ChargingWebSocketHandler.sendToUser(userId, message);
        ChargingWebSocketHandler.sendToOrder(orderNo, message);
    }

    @Override
    @Async
    public void sendChargeCompleteNotification(Long userId, ChargeOrder order) {
        log.info("发送充电完成实时通知: userId={}, orderNo={}", userId, order.getOrderNo());

        String stationName = "未知充电站";
        String connectorName = "未知充电桩";

        try {
            ChargingConnector connector = stationService.getConnectorById(order.getConnectorId());
            ChargingStation station = stationService.getById(connector.getStationId());
            stationName = station.getStationName();
            connectorName = connector.getConnectorName();
        } catch (Exception e) {
            log.warn("获取充电站信息失败: {}", e.getMessage());
        }

        Map<String, Object> message = new HashMap<>();
        message.put("type", "charge_complete");
        message.put("orderNo", order.getOrderNo());
        message.put("stationName", stationName);
        message.put("connectorName", connectorName);
        message.put("status", "completed");
        message.put("totalPower", order.getTotalPower());
        message.put("totalFee", order.getTotalFee());
        message.put("startTime", order.getStartTime() != null ? order.getStartTime().format(TIME_FORMATTER) : null);
        message.put("endTime", order.getEndTime() != null ? order.getEndTime().format(TIME_FORMATTER) : null);
        message.put("title", "充电完成");
        message.put("content", String.format("充电完成，本次充电%.2f度，费用%.2f元",
                order.getTotalPower() != null ? order.getTotalPower() : BigDecimal.ZERO,
                order.getTotalFee() != null ? order.getTotalFee() : BigDecimal.ZERO));
        message.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));

        ChargingWebSocketHandler.sendToUser(userId, message);
        ChargingWebSocketHandler.sendToOrder(order.getOrderNo(), message);
    }

    @Override
    @Async
    public void sendChargeFaultNotification(Long userId, String orderNo, String connectorId, String reason) {
        log.info("发送充电异常实时通知: userId={}, orderNo={}, reason={}", userId, orderNo, reason);

        Map<String, Object> message = new HashMap<>();
        message.put("type", "charge_fault");
        message.put("orderNo", orderNo);
        message.put("connectorId", connectorId);
        message.put("reason", reason);
        message.put("status", "fault");
        message.put("title", "充电异常");
        message.put("content", String.format("充电桩%s发生异常：%s", connectorId, reason));
        message.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));

        ChargingWebSocketHandler.sendToUser(userId, message);
        if (orderNo != null) {
            ChargingWebSocketHandler.sendToOrder(orderNo, message);
        }
    }

    @Override
    @Async
    public void sendPaymentSuccessNotification(Long userId, String orderNo, BigDecimal amount) {
        log.info("发送支付成功实时通知: userId={}, orderNo={}, amount={}", userId, orderNo, amount);

        Map<String, Object> message = new HashMap<>();
        message.put("type", "payment_success");
        message.put("orderNo", orderNo);
        message.put("amount", amount);
        message.put("title", "支付成功");
        message.put("content", String.format("订单%s支付成功，金额%.2f元", orderNo, amount));
        message.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));

        ChargingWebSocketHandler.sendToUser(userId, message);
        ChargingWebSocketHandler.sendToOrder(orderNo, message);
    }

    @Override
    @Async
    public void sendSystemNotification(Long userId, String title, String content) {
        log.info("发送系统通知: userId={}, title={}", userId, title);

        Map<String, Object> message = new HashMap<>();
        message.put("type", "system");
        message.put("title", title);
        message.put("content", content);
        message.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));

        if (userId != null) {
            ChargingWebSocketHandler.sendToUser(userId, message);
        } else {
            // 系统广播
            ChargingWebSocketHandler.broadcast(message);
        }
    }

    private String getChargeStatusText(Integer chargeStatus) {
        if (chargeStatus == null) return "未知";
        switch (chargeStatus) {
            case 1: return "启动中";
            case 2: return "充电中";
            case 3: return "停止中";
            case 4: return "已结束";
            case 5: return "未知";
            default: return "未知";
        }
    }
}
