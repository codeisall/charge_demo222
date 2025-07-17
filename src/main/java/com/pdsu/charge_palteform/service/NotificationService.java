package com.pdsu.charge_palteform.service;

import com.pdsu.charge_palteform.entity.ChargeOrder;
import com.pdsu.charge_palteform.entity.platefrom.charge.ChargeStatusData;

import java.math.BigDecimal;

public interface NotificationService {
    /**
     * 发送充电启动通知
     */
    void sendChargeStartNotification(Long userId, String orderNo, String stationName, String connectorId);

    /**
     * 发送充电状态变化通知
     */
    void sendChargeStatusNotification(Long userId, String orderNo, ChargeStatusData statusData);

    /**
     * 发送充电完成通知
     */
    void sendChargeCompleteNotification(Long userId, ChargeOrder order);

    /**
     * 发送充电异常通知
     */
    void sendChargeFaultNotification(Long userId, String orderNo, String connectorId, String reason);

    /**
     * 发送充电进度通知（实时）
     */
    void sendChargeProgressNotification(Long userId, String orderNo,
                                        BigDecimal soc, BigDecimal totalPower, BigDecimal totalFee, Integer chargeDuration);

    /**
     * 发送支付成功通知
     */
    void sendPaymentSuccessNotification(Long userId, String orderNo, BigDecimal amount);

    /**
     * 发送系统通知
     */
    void sendSystemNotification(Long userId, String title, String content);
}
