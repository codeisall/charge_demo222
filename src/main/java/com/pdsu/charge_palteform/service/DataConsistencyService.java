package com.pdsu.charge_palteform.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pdsu.charge_palteform.entity.ChargeOrder;
import com.pdsu.charge_palteform.entity.platefrom.charge.ChargeStatusData;
import com.pdsu.charge_palteform.enums.ChargeOrderStatusEnum;
import com.pdsu.charge_palteform.mapper.ChargeOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "data-sync.enabled", havingValue = "true", matchIfMissing = true)
public class DataConsistencyService {

    private final ChargeOrderMapper chargeOrderMapper;
    private final EnergyPlatformService energyPlatformService;
    private final OrderStateManagerService orderStateManagerService;
    private final NotificationService notificationService;

    /**
     * 每小时检查一次数据一致性
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void checkDataConsistency() {
        log.info("开始数据一致性检查...");
        try {
            // 检查长时间处于异常状态的订单
            checkStuckOrders();
            // 检查状态不一致的订单
            checkInconsistentOrders();
            // 检查缺失结束时间的完成订单
            checkMissingEndTimeOrders();
            log.info("数据一致性检查完成");
        } catch (Exception e) {
            log.error("数据一致性检查异常", e);
        }
    }

    /**
     * 检查长时间卡住的订单
     */
    private void checkStuckOrders() {
        LocalDateTime thresholdTime = LocalDateTime.now().minusHours(2);

        // 查找长时间处于"启动中"状态的订单
        List<ChargeOrder> stuckOrders = chargeOrderMapper.selectList(
                new LambdaQueryWrapper<ChargeOrder>()
                        .eq(ChargeOrder::getStatus, ChargeOrderStatusEnum.CHARGING.getCode())
                        .eq(ChargeOrder::getChargeStatus, 1) // 启动中
                        .lt(ChargeOrder::getUpdateTime, thresholdTime)
                        .isNotNull(ChargeOrder::getPlatformOrderNo)
        );

        if (!stuckOrders.isEmpty()) {
            log.warn("发现{}个可能卡住的订单", stuckOrders.size());

            for (ChargeOrder order : stuckOrders) {
                try {
                    log.info("检查卡住的订单: orderNo={}, lastUpdate={}",
                            order.getOrderNo(), order.getUpdateTime());

                    // 强制从平台查询状态
                    ChargeStatusData statusData = energyPlatformService.queryChargeStatus(order.getPlatformOrderNo());

                    if (statusData != null) {
                        orderStateManagerService.updateOrderStatusSafely(
                                order.getOrderNo(), statusData, "CONSISTENCY_CHECK");
                    } else {
                        // 平台查询不到，可能订单已异常
                        handleOrphanedOrder(order);
                    }

                } catch (Exception e) {
                    log.error("检查卡住订单{}失败", order.getOrderNo(), e);
                }
            }
        }
    }

    /**
     * 检查状态不一致的订单
     */
    private void checkInconsistentOrders() {
        // 查找状态可能不一致的订单：充电中但最后更新时间超过10分钟
        LocalDateTime thresholdTime = LocalDateTime.now().minusMinutes(10);

        List<ChargeOrder> inconsistentOrders = chargeOrderMapper.selectList(
                new LambdaQueryWrapper<ChargeOrder>()
                        .eq(ChargeOrder::getStatus, ChargeOrderStatusEnum.CHARGING.getCode())
                        .lt(ChargeOrder::getUpdateTime, thresholdTime)
                        .isNotNull(ChargeOrder::getPlatformOrderNo)
                        .last("LIMIT 10") // 限制检查数量
        );

        for (ChargeOrder order : inconsistentOrders) {
            try {
                log.debug("检查可能不一致的订单: orderNo={}", order.getOrderNo());

                ChargeStatusData statusData = energyPlatformService.queryChargeStatus(order.getPlatformOrderNo());

                if (statusData != null) {
                    // 检查状态是否真的不一致
                    boolean isInconsistent = false;

                    if (statusData.getChargeStatus() != null &&
                            !statusData.getChargeStatus().equals(order.getChargeStatus())) {
                        isInconsistent = true;
                    }

                    if (isInconsistent) {
                        log.info("发现状态不一致订单: orderNo={}, local={}, platform={}",
                                order.getOrderNo(), order.getChargeStatus(), statusData.getChargeStatus());

                        orderStateManagerService.updateOrderStatusSafely(
                                order.getOrderNo(), statusData, "INCONSISTENCY_FIX");
                    }
                }

            } catch (Exception e) {
                log.error("检查订单{}状态一致性失败", order.getOrderNo(), e);
            }
        }
    }

    /**
     * 检查缺失结束时间的完成订单
     */
    private void checkMissingEndTimeOrders() {
        List<ChargeOrder> ordersWithoutEndTime = chargeOrderMapper.selectList(
                new LambdaQueryWrapper<ChargeOrder>()
                        .eq(ChargeOrder::getStatus, ChargeOrderStatusEnum.COMPLETED.getCode())
                        .isNull(ChargeOrder::getEndTime)
                        .last("LIMIT 20")
        );

        for (ChargeOrder order : ordersWithoutEndTime) {
            try {
                log.info("修复缺失结束时间的订单: orderNo={}", order.getOrderNo());

                // 设置结束时间为最后更新时间
                order.setEndTime(order.getUpdateTime());
                chargeOrderMapper.updateById(order);

            } catch (Exception e) {
                log.error("修复订单{}结束时间失败", order.getOrderNo(), e);
            }
        }
    }

    /**
     * 处理孤儿订单（平台查询不到的订单）
     */
    private void handleOrphanedOrder(ChargeOrder order) {
        log.warn("发现孤儿订单，平台查询不到: orderNo={}", order.getOrderNo());

        try {
            // 将订单标记为异常
            order.setStatus(ChargeOrderStatusEnum.FAILED.getCode());
            order.setStopReason(1); // 平台停止
            order.setEndTime(LocalDateTime.now());
            chargeOrderMapper.updateById(order);

            // 通知用户
            notificationService.sendChargeFaultNotification(
                    order.getUserId(), order.getOrderNo(), order.getConnectorId(),
                    "系统检测到充电异常，订单已自动结束");

            log.info("孤儿订单{}已标记为异常", order.getOrderNo());

        } catch (Exception e) {
            log.error("处理孤儿订单{}失败", order.getOrderNo(), e);
        }
    }

    /**
     * 手动触发一致性检查（提供给管理接口调用）
     */
    public void manualConsistencyCheck() {
        log.info("手动触发数据一致性检查");
        checkDataConsistency();
    }

    /**
     * 检查指定订单的一致性
     */
    public boolean checkSingleOrderConsistency(String orderNo) {
        try {
            ChargeOrder order = chargeOrderMapper.selectOne(
                    new LambdaQueryWrapper<ChargeOrder>()
                            .eq(ChargeOrder::getOrderNo, orderNo)
            );

            if (order == null || order.getPlatformOrderNo() == null) {
                return false;
            }

            ChargeStatusData statusData = energyPlatformService.queryChargeStatus(order.getPlatformOrderNo());

            if (statusData != null) {
                return orderStateManagerService.updateOrderStatusSafely(
                        orderNo, statusData, "MANUAL_CHECK");
            }

            return false;

        } catch (Exception e) {
            log.error("检查订单{}一致性失败", orderNo, e);
            return false;
        }
    }
}
