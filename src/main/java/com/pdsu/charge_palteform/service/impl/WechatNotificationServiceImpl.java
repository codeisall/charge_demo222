package com.pdsu.charge_palteform.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaSubscribeMessage;
import com.pdsu.charge_palteform.config.NotificationConfig;
import com.pdsu.charge_palteform.entity.ChargeOrder;
import com.pdsu.charge_palteform.entity.NotificationRecord;
import com.pdsu.charge_palteform.entity.NotificationTemplate;
import com.pdsu.charge_palteform.entity.User;
import com.pdsu.charge_palteform.mapper.NotificationRecordMapper;
import com.pdsu.charge_palteform.service.NotificationService;
import com.pdsu.charge_palteform.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
@RequiredArgsConstructor
public class WechatNotificationServiceImpl implements NotificationService {

    private final WxMaService wxMaService;
    private final UserService userService;
    private final NotificationConfig notificationConfig;
    private final NotificationRecordMapper notificationRecordMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String TEMPLATE_CACHE_PREFIX = "notification:template:";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    @Override
    @Async
    public void sendChargeStartNotification(Long userId, String orderNo, String stationName) {
        log.info("发送充电启动通知: userId={}, orderNo={}, stationName={}", userId, orderNo, stationName);

        try {
            User user = userService.getUserById(userId);
            if (user == null || user.getOpenid() == null) {
                log.warn("用户不存在或未绑定微信: userId={}", userId);
                return;
            }

            // 构建通知模板
            NotificationTemplate template = buildChargeStartTemplate(orderNo, stationName);

            // 发送通知
            boolean success = sendWechatTemplateMessage(user.getOpenid(), template);

            // 记录通知
            recordNotification(userId, "charge_start", "充电启动通知",
                    String.format("您在%s的充电已开始", stationName),
                    template.getTemplateId(), orderNo, success, null);

        } catch (Exception e) {
            log.error("发送充电启动通知失败: userId={}, orderNo={}", userId, orderNo, e);
            recordNotification(userId, "charge_start", "充电启动通知", "",
                    null, orderNo, false, e.getMessage());
        }
    }

    @Override
    @Async
    public void sendChargeCompleteNotification(Long userId, ChargeOrder order) {
        log.info("发送充电完成通知: userId={}, orderNo={}", userId, order.getOrderNo());
        try {
            User user = userService.getUserById(userId);
            if (user == null || user.getOpenid() == null) {
                log.warn("用户不存在或未绑定微信: userId={}", userId);
                return;
            }
            // 构建通知模板
            NotificationTemplate template = buildChargeCompleteTemplate(order);
            // 发送通知
            boolean success = sendWechatTemplateMessage(user.getOpenid(), template);
            // 记录通知
            recordNotification(userId, "charge_complete", "充电完成通知",
                    String.format("充电完成，本次充电%.2f度，费用%.2f元",
                            order.getTotalPower(), order.getActualPayment() != null ? order.getActualPayment() : order.getTotalFee()),
                    template.getTemplateId(), order.getOrderNo(), success, null);
        } catch (Exception e) {
            log.error("发送充电完成通知失败: userId={}, orderNo={}", userId, order.getOrderNo(), e);
            recordNotification(userId, "charge_complete", "充电完成通知", "",
                    null, order.getOrderNo(), false, e.getMessage());
        }
    }

    @Override
    @Async
    public void sendChargeFaultNotification(Long userId, String connectorId, String reason) {
        log.info("发送充电异常通知: userId={}, connectorId={}, reason={}", userId, connectorId, reason);

        try {
            User user = userService.getUserById(userId);
            if (user == null || user.getOpenid() == null) {
                log.warn("用户不存在或未绑定微信: userId={}", userId);
                return;
            }

            // 构建通知模板
            NotificationTemplate template = buildChargeFaultTemplate(connectorId, reason);

            // 发送通知
            boolean success = sendWechatTemplateMessage(user.getOpenid(), template);

            // 记录通知
            recordNotification(userId, "charge_fault", "充电异常通知",
                    String.format("充电桩%s发生异常：%s", connectorId, reason),
                    template.getTemplateId(), null, success, null);

        } catch (Exception e) {
            log.error("发送充电异常通知失败: userId={}, connectorId={}", userId, connectorId, e);
            recordNotification(userId, "charge_fault", "充电异常通知", "",
                    null, null, false, e.getMessage());
        }
    }

    @Override
    @Async
    public void sendChargeProgressNotification(Long userId, String orderNo, BigDecimal soc, BigDecimal totalFee) {
        log.info("发送充电进度通知: userId={}, orderNo={}, soc={}, totalFee={}", userId, orderNo, soc, totalFee);

        try {
            // 检查是否需要发送进度通知（避免频繁推送）
            String cacheKey = "notification:progress:" + orderNo;
            String lastSent = redisTemplate.opsForValue().get(cacheKey);

            if (lastSent != null) {
                log.debug("充电进度通知已在5分钟内发送过，跳过: orderNo={}", orderNo);
                return;
            }

            User user = userService.getUserById(userId);
            if (user == null || user.getOpenid() == null) {
                log.warn("用户不存在或未绑定微信: userId={}", userId);
                return;
            }

            // 构建通知模板
            NotificationTemplate template = buildChargeProgressTemplate(orderNo, soc, totalFee);

            // 发送通知
            boolean success = sendWechatTemplateMessage(user.getOpenid(), template);

            if (success) {
                // 设置5分钟缓存，避免频繁推送
                redisTemplate.opsForValue().set(cacheKey, "sent", 5, TimeUnit.MINUTES);
            }

            // 记录通知
            recordNotification(userId, "charge_progress", "充电进度通知",
                    String.format("当前电量%s%%，已消费%.2f元", soc, totalFee),
                    template.getTemplateId(), orderNo, success, null);

        } catch (Exception e) {
            log.error("发送充电进度通知失败: userId={}, orderNo={}", userId, orderNo, e);
            recordNotification(userId, "charge_progress", "充电进度通知", "",
                    null, orderNo, false, e.getMessage());
        }
    }

    @Override
    @Async
    public void sendPaymentSuccessNotification(Long userId, String orderNo, BigDecimal amount) {
        log.info("发送支付成功通知: userId={}, orderNo={}, amount={}", userId, orderNo, amount);

        try {
            User user = userService.getUserById(userId);
            if (user == null || user.getOpenid() == null) {
                log.warn("用户不存在或未绑定微信: userId={}", userId);
                return;
            }

            // 构建通知模板
            NotificationTemplate template = buildPaymentSuccessTemplate(orderNo, amount);

            // 发送通知
            boolean success = sendWechatTemplateMessage(user.getOpenid(), template);

            // 记录通知
            recordNotification(userId, "payment_success", "支付成功通知",
                    String.format("订单%s支付成功，金额%.2f元", orderNo, amount),
                    template.getTemplateId(), orderNo, success, null);

        } catch (Exception e) {
            log.error("发送支付成功通知失败: userId={}, orderNo={}", userId, orderNo, e);
            recordNotification(userId, "payment_success", "支付成功通知", "",
                    null, orderNo, false, e.getMessage());
        }
    }

    /**
     * 发送微信模板消息
     */
    private boolean sendWechatTemplateMessage(String openid, NotificationTemplate template) {
        try {
            if (!notificationConfig.getWechat().isEnabled()) {
                log.debug("微信通知已禁用，跳过发送");
                return false;
            }
            WxMaSubscribeMessage message = new WxMaSubscribeMessage();
            message.setToUser(openid);
            message.setTemplateId(template.getTemplateId());
            message.setPage(template.getPage());
            message.setMiniprogramState(template.getMiniProgramState());
            if (template.getData() != null && !template.getData().isEmpty()) {
                template.getData().forEach((key, value) -> {
                    WxMaSubscribeMessage.MsgData msgData = new WxMaSubscribeMessage.MsgData();
                    msgData.setName(key);    // 设置模板字段名
                    msgData.setValue(value); // 设置模板字段值
                    message.addData(msgData); // 正确调用单参数方法
                });
            }
            wxMaService.getMsgService().sendSubscribeMsg(message);
            log.info("微信模板消息发送成功: openid={}, templateId={}", openid, template.getTemplateId());
            return true;
        } catch (Exception e) {
            log.error("微信模板消息发送失败: openid={}, templateId={}", openid, template.getTemplateId(), e);
            return false;
        }
    }


    private NotificationTemplate buildChargeStartTemplate(String orderNo, String stationName) {
        NotificationTemplate template = new NotificationTemplate();
        template.setTemplateId(notificationConfig.getWechat().getTemplateId("charge-start"));
        template.setTitle("充电启动通知");
        template.setPage("pages/charge/detail?orderNo=" + orderNo);
        template.setMiniProgramState("formal");

        Map<String, String> data = new HashMap<>();
        data.put("thing1", stationName); // 充电站名称
        data.put("character_string2", orderNo); // 订单编号
        data.put("time3", LocalDateTime.now().format(TIME_FORMATTER)); // 开始时间
        data.put("thing4", "充电已开始，请注意充电状态"); // 备注
        template.setData(data);

        return template;
    }


    private NotificationTemplate buildChargeCompleteTemplate(ChargeOrder order) {
        NotificationTemplate template = new NotificationTemplate();
        template.setTemplateId(notificationConfig.getWechat().getTemplateId("charge-complete"));
        template.setTitle("充电完成通知");
        template.setPage("pages/charge/detail?orderNo=" + order.getOrderNo());
        template.setMiniProgramState("formal");

        Map<String, String> data = new HashMap<>();
        data.put("character_string1", order.getOrderNo()); // 订单编号
        data.put("thing2", String.format("%.2f度", order.getTotalPower())); // 充电量
        data.put("amount3", String.format("%.2f元",
                order.getActualPayment() != null ? order.getActualPayment() : order.getTotalFee())); // 费用金额
        data.put("time4", order.getEndTime() != null ?
                order.getEndTime().format(TIME_FORMATTER) :
                LocalDateTime.now().format(TIME_FORMATTER)); // 完成时间
        data.put("thing5", "充电已完成，感谢使用"); // 备注
        template.setData(data);

        return template;
    }

    private NotificationTemplate buildChargeFaultTemplate(String connectorId, String reason) {
        NotificationTemplate template = new NotificationTemplate();
        template.setTemplateId(notificationConfig.getWechat().getTemplateId("charge-fault"));
        template.setTitle("充电异常通知");
        template.setPage("pages/charge/list");
        template.setMiniProgramState("formal");

        Map<String, String> data = new HashMap<>();
        data.put("thing1", connectorId); // 充电桩编号
        data.put("phrase2", "充电异常"); // 异常状态
        data.put("time3", LocalDateTime.now().format(TIME_FORMATTER)); // 异常时间
        data.put("thing4", reason); // 异常原因
        data.put("thing5", "请联系客服或重新选择充电桩"); // 处理建议
        template.setData(data);

        return template;
    }

    /**
     * 构建充电进度通知模板
     */
    private NotificationTemplate buildChargeProgressTemplate(String orderNo, BigDecimal soc, BigDecimal totalFee) {
        NotificationTemplate template = new NotificationTemplate();
        template.setTemplateId(notificationConfig.getWechat().getTemplateId("charge-progress"));
        template.setTitle("充电进度通知");
        template.setPage("pages/charge/detail?orderNo=" + orderNo);
        template.setMiniProgramState("formal");

        Map<String, String> data = new HashMap<>();
        data.put("character_string1", orderNo); // 订单编号
        data.put("thing2", String.format("%s%%", soc)); // 当前电量
        data.put("amount3", String.format("%.2f元", totalFee)); // 当前费用
        data.put("time4", LocalDateTime.now().format(TIME_FORMATTER)); // 当前时间
        data.put("thing5", "充电进行中"); // 状态
        template.setData(data);

        return template;
    }

    /**
     * 构建支付成功通知模板
     */
    private NotificationTemplate buildPaymentSuccessTemplate(String orderNo, BigDecimal amount) {
        NotificationTemplate template = new NotificationTemplate();
        template.setTemplateId(notificationConfig.getWechat().getTemplateId("payment-success"));
        template.setTitle("支付成功通知");
        template.setPage("pages/charge/detail?orderNo=" + orderNo);
        template.setMiniProgramState("formal");

        Map<String, String> data = new HashMap<>();
        data.put("character_string1", orderNo); // 订单编号
        data.put("amount2", String.format("%.2f元", amount)); // 支付金额
        data.put("time3", LocalDateTime.now().format(TIME_FORMATTER)); // 支付时间
        data.put("thing4", "微信支付"); // 支付方式
        data.put("thing5", "支付成功，感谢使用"); // 备注
        template.setData(data);

        return template;
    }

    /**
     * 记录通知发送记录
     */
    private void recordNotification(Long userId, String type, String title, String content,
                                    String templateId, String orderNo, boolean success, String errorMessage) {
        try {
            NotificationRecord record = new NotificationRecord();
            record.setUserId(userId);
            record.setType(type);
            record.setTitle(title);
            record.setContent(content);
            record.setTemplateId(templateId);
            record.setRelateOrderNo(orderNo);
            record.setChannel(1); // 微信模板消息
            record.setStatus(success ? 2 : 3); // 2-成功，3-失败
            record.setErrorMessage(errorMessage);
            record.setRetryCount(0);
            record.setSendTime(LocalDateTime.now());

            notificationRecordMapper.insert(record);
        } catch (Exception e) {
            log.error("记录通知发送记录失败", e);
        }
    }




}
