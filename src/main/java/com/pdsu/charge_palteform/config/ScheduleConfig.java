package com.pdsu.charge_palteform.config;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pdsu.charge_palteform.entity.ChargeOrder;
import com.pdsu.charge_palteform.entity.platefrom.charge.ChargeStatusData;
import com.pdsu.charge_palteform.mapper.ChargeOrderMapper;
import com.pdsu.charge_palteform.service.DataSyncService;
import com.pdsu.charge_palteform.service.EnergyPlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduleConfig {
    private final DataSyncService dataSyncService;

    private final ChargeOrderMapper chargeOrderMapper;
    private final EnergyPlatformService energyPlatformService;

    /**
     * 每天凌晨2点同步充电站基础信息
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void syncStationInfo() {
        log.info("🕐 开始定时同步充电站基础信息...");
        try {
            dataSyncService.syncStationInfo();
            log.info("✅ 定时同步充电站基础信息完成");
        } catch (Exception e) {
            log.error("❌ 定时同步充电站基础信息失败", e);
        }
    }

    /**
     * 每5分钟同步充电桩状态
     */
    @Scheduled(fixedRate = 300000) // 5分钟 = 300000毫秒
    public void syncConnectorStatus() {
        log.debug("🔄 开始定时同步充电桩状态...");
        try {
            dataSyncService.syncConnectorStatus();
            log.debug("✅ 定时同步充电桩状态完成");
        } catch (Exception e) {
            log.error("❌ 定时同步充电桩状态失败", e);
        }
    }

    /**
     * 启动5分钟后执行一次基础数据同步（用于处理启动时同步失败的情况）
     */
    @Scheduled(initialDelay = 300000, fixedRate = Long.MAX_VALUE) // 5分钟后执行一次
    public void delayedInitSync() {
        log.info("🚀 执行延迟初始化数据同步...");
        try {
            dataSyncService.fullSync();
            log.info("✅ 延迟初始化数据同步完成");
        } catch (Exception e) {
            log.error("❌ 延迟初始化数据同步失败", e);
        }
    }

    @Scheduled(fixedRate = 30000)
    public void syncChargingOrderStatus() {
        log.debug("🔄 开始同步充电中订单状态和费用...");
        try {
            // 查询所有充电中的订单
            List<ChargeOrder> chargingOrders = chargeOrderMapper.selectList(
                    new LambdaQueryWrapper<ChargeOrder>()
                            .eq(ChargeOrder::getStatus, 2) // 充电中
                            .isNotNull(ChargeOrder::getPlatformOrderNo)
                            .orderByDesc(ChargeOrder::getUpdateTime)
                            .last("LIMIT 20") // 限制一次处理20个订单
            );

            if (chargingOrders.isEmpty()) {
                log.debug("没有需要同步的充电中订单");
                return;
            }

            log.info("开始同步{}个充电中订单的状态和费用", chargingOrders.size());
            int successCount = 0;

            for (ChargeOrder order : chargingOrders) {
                try {
                    boolean updated = syncSingleOrderStatus(order);
                    if (updated) {
                        successCount++;
                    }
                    // 避免请求过于频繁
                    Thread.sleep(100);
                } catch (Exception e) {
                    log.error("同步订单{}状态失败: {}", order.getOrderNo(), e.getMessage());
                }
            }

            log.debug("✅ 充电订单状态同步完成，成功更新{}个订单", successCount);

        } catch (Exception e) {
            log.error("❌ 同步充电订单状态任务异常", e);
        }
    }

    @Scheduled(fixedRate =  300000) // 5分钟
    public void checkAbnormalOrders() {
        log.debug("🔍 开始检查异常订单...");
        try {
            // 检查超过30分钟仍在"启动中"状态的订单
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
            List<ChargeOrder> abnormalOrders = chargeOrderMapper.selectList(
                    new LambdaQueryWrapper<ChargeOrder>()
                            .eq(ChargeOrder::getStatus, 2) // 充电中
                            .eq(ChargeOrder::getChargeStatus, 1) // 启动中
                            .lt(ChargeOrder::getStartTime, threshold)
                            .isNotNull(ChargeOrder::getPlatformOrderNo)
            );
            if (!abnormalOrders.isEmpty()) {
                log.warn("⚠️ 发现{}个可能异常的订单（启动中状态超过30分钟）", abnormalOrders.size());

                for (ChargeOrder order : abnormalOrders) {
                    log.warn("异常订单: orderNo={}, startTime={}, chargeStatus={}",
                            order.getOrderNo(), order.getStartTime(), order.getChargeStatus());
                    // 强制同步一次状态
                    try {
                        syncSingleOrderStatus(order);
                    } catch (Exception e) {
                        log.error("强制同步异常订单{}失败: {}", order.getOrderNo(), e.getMessage());
                    }
                }
            } else {
                log.debug("✅ 未发现异常订单");
            }

        } catch (Exception e) {
            log.error("❌ 检查异常订单任务失败", e);
        }
    }


    private boolean syncSingleOrderStatus(ChargeOrder order) {
        try {
            log.debug("同步订单: orderNo={}, platformOrderNo={}",
                    order.getOrderNo(), order.getPlatformOrderNo());

            // 从第三方平台查询最新状态
            ChargeStatusData statusData = energyPlatformService.queryChargeStatus(order.getPlatformOrderNo());

            if (statusData == null) {
                log.debug("未获取到订单{}的状态数据", order.getOrderNo());
                return false;
            }

            boolean needUpdate = false;
            ChargeOrder updateOrder = new ChargeOrder();
            updateOrder.setId(order.getId());

            // 检查充电状态变化
            if (statusData.getChargeStatus() != null && !statusData.getChargeStatus().equals(order.getChargeStatus())) {
                updateOrder.setChargeStatus(statusData.getChargeStatus());
                needUpdate = true;
                log.info("订单{}充电状态更新: {} -> {}", order.getOrderNo(), order.getChargeStatus(), statusData.getChargeStatus());
            }

            // 检查订单状态变化
            Integer newOrderStatus = mapChargeStatusToOrderStatus(statusData.getChargeStatus());
            if (newOrderStatus != null && !newOrderStatus.equals(order.getStatus())) {
                updateOrder.setStatus(newOrderStatus);
                needUpdate = true;
                log.info("订单{}状态更新: {} -> {}", order.getOrderNo(), order.getStatus(), newOrderStatus);

                // 如果状态变为充电完成，且没有停止原因，设置为平台停止
                if (newOrderStatus == 3 && order.getStopReason() == null) {
                    updateOrder.setStopReason(1); // 1-平台停止
                    log.info("订单{}设置停止原因为平台停止", order.getOrderNo());
                }
            }

            // 更新充电电量
            if (statusData.getTotalPower() != null &&
                    statusData.getTotalPower().compareTo(order.getTotalPower() != null ? order.getTotalPower() : BigDecimal.ZERO) != 0) {
                updateOrder.setTotalPower(statusData.getTotalPower());
                needUpdate = true;
                log.debug("订单{}充电量更新: {} -> {} kWh", order.getOrderNo(), order.getTotalPower(), statusData.getTotalPower());
            }

            // 更新电费
            if (statusData.getElectricityFee() != null &&
                    statusData.getElectricityFee().compareTo(order.getElectricityFee() != null ? order.getElectricityFee() : BigDecimal.ZERO) != 0) {
                updateOrder.setElectricityFee(statusData.getElectricityFee());
                needUpdate = true;
                log.debug("订单{}电费更新: {} -> {} 元", order.getOrderNo(), order.getElectricityFee(), statusData.getElectricityFee());
            }

            // 更新服务费
            if (statusData.getServiceFee() != null &&
                    statusData.getServiceFee().compareTo(order.getServiceFee() != null ? order.getServiceFee() : BigDecimal.ZERO) != 0) {
                updateOrder.setServiceFee(statusData.getServiceFee());
                needUpdate = true;
                log.debug("订单{}服务费更新: {} -> {} 元", order.getOrderNo(), order.getServiceFee(), statusData.getServiceFee());
            }

            // 更新总金额
            if (statusData.getTotalFee() != null &&
                    statusData.getTotalFee().compareTo(order.getTotalFee() != null ? order.getTotalFee() : BigDecimal.ZERO) != 0) {
                updateOrder.setTotalFee(statusData.getTotalFee());
                needUpdate = true;
                log.info("订单{}总费用更新: {} -> {} 元", order.getOrderNo(), order.getTotalFee(), statusData.getTotalFee());
            }

            // 更新结束时间
            if (statusData.getEndTime() != null && order.getEndTime() == null) {
                updateOrder.setEndTime(statusData.getEndTime());
                needUpdate = true;
                log.info("订单{}设置结束时间: {}", order.getOrderNo(), statusData.getEndTime());
            }

            // 如果有变化，则更新数据库
            if (needUpdate) {
                int updateResult = chargeOrderMapper.updateById(updateOrder);
                if (updateResult > 0) {
                    log.debug("订单{}状态和费用同步成功", order.getOrderNo());
                    return true;
                } else {
                    log.warn("订单{}状态更新失败", order.getOrderNo());
                    return false;
                }
            }

            return false;

        } catch (Exception e) {
            log.error("同步订单{}状态异常: {}", order.getOrderNo(), e.getMessage());
            return false;
        }
    }

    /**
     * 充电状态到订单状态的映射
     */
    private Integer mapChargeStatusToOrderStatus(Integer chargeStatus) {
        if (chargeStatus == null) return null;

        switch (chargeStatus) {
            case 1: return 2; // 启动中 -> 充电中
            case 2: return 2; // 充电中 -> 充电中
            case 3: return 2; // 停止中 -> 充电中
            case 4: return 3; // 已结束 -> 充电完成
            case 5: return null; // 未知 -> 不变更
            default: return null;
        }
    }


}
