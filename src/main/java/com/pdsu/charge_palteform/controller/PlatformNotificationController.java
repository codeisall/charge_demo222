package com.pdsu.charge_palteform.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdsu.charge_palteform.entity.ChargeOrder;
import com.pdsu.charge_palteform.entity.platefrom.PlatformRequest;
import com.pdsu.charge_palteform.entity.platefrom.PlatformResponse;
import com.pdsu.charge_palteform.entity.platefrom.charge.ChargeStatusData;
import com.pdsu.charge_palteform.entity.platefrom.station.ConnectorStatusInfo;
import com.pdsu.charge_palteform.service.ChargeOrderService;
import com.pdsu.charge_palteform.service.NotificationService;
import com.pdsu.charge_palteform.service.OrderStateManagerService;
import com.pdsu.charge_palteform.utils.AesUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "三方平台推送接口", description = "接收电能平台的实时推送数据")
@Slf4j
@RestController
@RequestMapping("/api/platform/notification")
@RequiredArgsConstructor
public class PlatformNotificationController {

    private final NotificationService notificationService;
    private final ChargeOrderService chargeOrderService;
    private final OrderStateManagerService orderStateManagerService;
    private final ObjectMapper objectMapper;


    @Value("${energy.platform.dataSecret}")
    private String dataSecret;

    @Value("${energy.platform.dataSecretIV}")
    private String dataSecretIv;

    @Operation(summary = "设备状态变化推送", description = "接收电能平台推送的设备状态变化")
    @PostMapping("/station_status")
    public PlatformResponse handleStationStatusPush(@RequestBody PlatformRequest request) {
        log.info("收到充电状态推送: {}", request.getOperatorID());
        try {
            // 解密数据
            String decryptedData = AesUtil.decrypt(request.getData(), dataSecret, dataSecretIv);
            log.info("解密后的充电状态数据: {}", decryptedData);
            // 解析充电状态
            ChargeStatusData statusData = objectMapper.readValue(decryptedData, ChargeStatusData.class);
            // 根据平台订单号查找本地订单
            ChargeOrder order = chargeOrderService.getByPlatformOrderNo(statusData.getPlatformOrderNo());
            if (order != null) {
                // 使用状态管理服务统一处理更新
                orderStateManagerService.updateOrderStatusSafely(
                        order.getOrderNo(), statusData, "PLATFORM_PUSH");
                log.info("充电状态推送处理成功: orderNo={}", order.getOrderNo());
            } else {
                log.warn("未找到对应的本地订单: platformOrderNo={}", statusData.getPlatformOrderNo());
            }
            return buildSuccessResponse();
        } catch (Exception e) {
            log.error("处理充电状态推送失败", e);
            return buildErrorResponse("处理失败");
        }
    }

    @Operation(summary = "充电状态推送", description = "接收电能平台推送的充电状态变化")
    @PostMapping("/charge_status")
    public PlatformResponse handleChargeStatusPush(@RequestBody PlatformRequest request) {
        log.info("收到充电状态推送: {}", request.getOperatorID());
        try {
            // 解密数据
            String decryptedData = AesUtil.decrypt(request.getData(), dataSecret, dataSecretIv);
            log.info("解密后的充电状态数据: {}", decryptedData);
            // 解析充电状态
            ChargeStatusData statusData = objectMapper.readValue(decryptedData, ChargeStatusData.class);
            // 根据平台订单号查找本地订单
            ChargeOrder order = chargeOrderService.getOne(
                    new LambdaQueryWrapper<ChargeOrder>()
                            .eq(ChargeOrder::getPlatformOrderNo, statusData.getPlatformOrderNo())
            );

            if (order != null) {
                // 实时推送给用户
                notificationService.sendChargeStatusNotification(
                        order.getUserId(), order.getOrderNo(), statusData);

                // 如果有进度数据，发送进度通知
                if (statusData.getTotalPower() != null || statusData.getSoc() != null) {
                    Integer chargeDuration = calculateChargeDuration(order.getStartTime());
                    notificationService.sendChargeProgressNotification(
                            order.getUserId(),
                            order.getOrderNo(),
                            statusData.getSoc(),
                            statusData.getTotalPower(),
                            statusData.getTotalFee(),
                            chargeDuration
                    );
                }
                log.info("充电状态推送处理成功: orderNo={}", order.getOrderNo());
            } else {
                log.warn("未找到对应的本地订单: platformOrderNo={}", statusData.getPlatformOrderNo());
            }
            return buildSuccessResponse();
        } catch (Exception e) {
            log.error("处理充电状态推送失败", e);
            return buildErrorResponse("处理失败");
        }
    }

    @Operation(summary = "充电完成推送", description = "接收电能平台推送的充电完成信息")
    @PostMapping("/charge_complete")
    public PlatformResponse handleChargeCompletePush(@RequestBody PlatformRequest request) {
        log.info("收到充电完成推送: {}", request.getOperatorID());
        try {
            // 解密数据
            String decryptedData = AesUtil.decrypt(request.getData(), dataSecret, dataSecretIv);
            log.info("解密后的充电完成数据: {}", decryptedData);
            // 解析完成信息
            Map<String, Object> completeData = objectMapper.readValue(decryptedData, Map.class);
            String platformOrderNo = (String) completeData.get("StartChargeSeq");
            // 查找本地订单
            ChargeOrder order = chargeOrderService.getByPlatformOrderNo(platformOrderNo);
            if (order != null) {
                // 构建完成状态数据
                ChargeStatusData statusData = new ChargeStatusData();
                statusData.setPlatformOrderNo(platformOrderNo);
                statusData.setChargeStatus(4); // 已结束
                statusData.setEndTime(LocalDateTime.now());
                // 更新费用信息
                if (completeData.containsKey("TotalPower")) {
                    statusData.setTotalPower(new BigDecimal(completeData.get("TotalPower").toString()));
                }
                if (completeData.containsKey("TotalMoney")) {
                    statusData.setTotalFee(new BigDecimal(completeData.get("TotalMoney").toString()));
                }
                if (completeData.containsKey("TotalElecMoney")) {
                    statusData.setElectricityFee(new BigDecimal(completeData.get("TotalElecMoney").toString()));
                }
                if (completeData.containsKey("TotalSeviceMoney")) {
                    statusData.setServiceFee(new BigDecimal(completeData.get("TotalSeviceMoney").toString()));
                }
                // 使用状态管理服务统一处理更新
                orderStateManagerService.updateOrderStatusSafely(
                        order.getOrderNo(), statusData, "PLATFORM_COMPLETE");
                log.info("充电完成推送处理成功: orderNo={}", order.getOrderNo());
            } else {
                log.warn("未找到对应的本地订单: platformOrderNo={}", platformOrderNo);
            }
            return buildSuccessResponse();
        } catch (Exception e) {
            log.error("处理充电完成推送失败", e);
            return buildErrorResponse("处理失败");
        }
    }

    @Operation(summary = "充电异常推送", description = "接收电能平台推送的充电异常信息")
    @PostMapping("/charge_fault")
    public PlatformResponse handleChargeFaultPush(@RequestBody PlatformRequest request) {
        log.info("收到充电异常推送: {}", request.getOperatorID());
        try {
            // 解密数据
            String decryptedData = AesUtil.decrypt(request.getData(), dataSecret, dataSecretIv);
            log.info("解密后的充电异常数据: {}", decryptedData);
            // 解析异常信息
            Map<String, Object> faultData = objectMapper.readValue(decryptedData, Map.class);
            String connectorId = (String) faultData.get("ConnectorID");
            String reason = (String) faultData.get("FaultReason");
            String platformOrderNo = (String) faultData.get("StartChargeSeq");
            // 查找相关订单
            ChargeOrder order = null;
            if (platformOrderNo != null) {
                order = chargeOrderService.getByPlatformOrderNo(platformOrderNo);
            }
            if (order != null) {
                // 直接更新为异常状态（不通过状态管理服务，因为这是特殊情况）
                order.setStatus(6); // 异常
                order.setStopReason(3); // 设备故障
                chargeOrderService.updateById(order);
                // 发送异常通知
                notificationService.sendChargeFaultNotification(
                        order.getUserId(), order.getOrderNo(), connectorId, reason);
                log.info("充电异常推送处理成功: orderNo={}, reason={}", order.getOrderNo(), reason);
            } else {
                log.warn("未找到对应的本地订单，直接推送异常通知: connectorId={}", connectorId);
                // TODO: 可以根据connectorId查找正在使用该桩的用户
            }
            return buildSuccessResponse();
        } catch (Exception e) {
            log.error("处理充电异常推送失败", e);
            return buildErrorResponse("处理失败");
        }
    }


    private Integer calculateChargeDuration(LocalDateTime startTime) {
        if (startTime == null) return 0;
        return (int) java.time.Duration.between(startTime, LocalDateTime.now()).toMinutes();
    }


    private void handleConnectorStatusChange(ConnectorStatusInfo statusInfo) {
        // 这里可以更新本地充电桩状态
        // 如果有用户正在关注该充电桩，可以推送状态变化
        log.info("充电桩{}状态变化: {}", statusInfo.getConnectorID(), statusInfo.getStatus());
        // 可以实现：
        // 1. 更新本地充电桩状态
        // 2. 如果状态变为故障，通知正在充电的用户
        // 3. 如果状态变为空闲，通知等待的用户
    }

    /**
     * 构建成功响应
     */
    private PlatformResponse buildSuccessResponse() {
        PlatformResponse response = new PlatformResponse();
        response.setRet(0);
        response.setMsg("success");
        return response;
    }

    /**
     * 构建错误响应
     */
    private PlatformResponse buildErrorResponse(String message) {
        PlatformResponse response = new PlatformResponse();
        response.setRet(1);
        response.setMsg(message);
        return response;
    }





}
