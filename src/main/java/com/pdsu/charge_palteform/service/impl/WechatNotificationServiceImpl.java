package com.pdsu.charge_palteform.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import com.pdsu.charge_palteform.entity.ChargeOrder;
import com.pdsu.charge_palteform.service.NotificationService;
import com.pdsu.charge_palteform.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;


@Slf4j
@Service
@RequiredArgsConstructor
public class WechatNotificationServiceImpl implements NotificationService {

    private final WxMaService wxMaService;
    private final UserService userService;


    @Value("${wechat.miniapp.templates.charge-start}")
    private String chargeStartTemplateId;

    @Value("${wechat.miniapp.templates.charge-complete}")
    private String chargeCompleteTemplateId;

    @Value("${wechat.miniapp.templates.charge-fault}")
    private String chargeFaultTemplateId;

    @Value("${wechat.miniapp.templates.charge-progress}")
    private String chargeProgressTemplateId;

    @Value("${wechat.miniapp.templates.payment-success}")
    private String paymentSuccessTemplateId;


    @Override
    public void sendChargeStartNotification(Long userId, String orderNo, String stationName) {

    }

    @Override
    public void sendChargeCompleteNotification(Long userId, ChargeOrder order) {

    }

    @Override
    public void sendChargeFaultNotification(Long userId, String connectorId, String reason) {

    }

    @Override
    public void sendChargeProgressNotification(Long userId, String orderNo, BigDecimal soc, BigDecimal totalFee) {

    }

    @Override
    public void sendPaymentSuccessNotification(Long userId, String orderNo, BigDecimal amount) {

    }
}
