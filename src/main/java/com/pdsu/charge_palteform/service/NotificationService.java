package com.pdsu.charge_palteform.service;

import com.pdsu.charge_palteform.entity.ChargeOrder;

import java.math.BigDecimal;

public interface NotificationService {
    /**
     * 发送充电启动通知
     */
    void sendChargeStartNotification(Long userId, String orderNo, String stationName);

    /**
     * 发送充电完成通知
     */
    void sendChargeCompleteNotification(Long userId, ChargeOrder order);

    /**
     * 发送充电异常通知
     */
    void sendChargeFaultNotification(Long userId, String connectorId, String reason);

    /**
     * 发送充电进度通知
     */
    void sendChargeProgressNotification(Long userId, String orderNo,
                                        BigDecimal soc, BigDecimal totalFee);

    /**
     * 发送支付成功通知
     */
    void sendPaymentSuccessNotification(Long userId, String orderNo, BigDecimal amount);
}
