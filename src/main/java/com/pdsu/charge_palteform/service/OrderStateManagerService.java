package com.pdsu.charge_palteform.service;


import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pdsu.charge_palteform.entity.ChargeOrder;
import com.pdsu.charge_palteform.entity.platefrom.charge.ChargeStatusData;
import com.pdsu.charge_palteform.enums.ChargeOrderStatusEnum;
import com.pdsu.charge_palteform.enums.PlatformChargeStatusEnum;
import com.pdsu.charge_palteform.mapper.ChargeOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStateManagerService {

    private final ChargeOrderMapper chargeOrderMapper;
    private final StringRedisTemplate redisTemplate;
    private final NotificationService notificationService;

    private static final String ORDER_LOCK_PREFIX = "order:lock:";
    private static final int LOCK_EXPIRE_SECONDS = 30;

    /**
     * 安全更新订单状态（带分布式锁）
     */
    @Transactional
    public boolean updateOrderStatusSafely(String orderNo, ChargeStatusData statusData, String source) {
        String lockKey = ORDER_LOCK_PREFIX + orderNo;

        try {
            // 获取分布式锁
            Boolean lockResult = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, source, LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);

            if (!lockResult) {
                log.debug("获取订单{}锁失败，跳过本次更新，来源: {}", orderNo, source);
                return false;
            }

            log.debug("获取订单{}锁成功，开始更新状态，来源: {}", orderNo, source);

            // 查询当前订单状态
            ChargeOrder currentOrder = chargeOrderMapper.selectOne(
                    new LambdaUpdateWrapper<ChargeOrder>()
                            .eq(ChargeOrder::getOrderNo, orderNo)
            );

            if (currentOrder == null) {
                log.warn("订单{}不存在", orderNo);
                return false;
            }

            // 构建更新对象
            ChargeOrderUpdate updateData = buildUpdateData(currentOrder, statusData);

            if (!updateData.hasChanges()) {
                log.debug("订单{}无状态变化，跳过更新", orderNo);
                return false;
            }

            // 执行数据库更新
            boolean updateResult = executeOrderUpdate(currentOrder.getId(), updateData);

            if (updateResult) {
                // 发送实时通知
                sendRealtimeNotifications(currentOrder, updateData, statusData);
                log.info("订单{}状态更新成功，来源: {}", orderNo, source);
            }

            return updateResult;

        } catch (Exception e) {
            log.error("更新订单{}状态异常，来源: {}", orderNo, source, e);
            return false;
        } finally {
            // 释放锁
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 构建订单更新数据
     */
    private ChargeOrderUpdate buildUpdateData(ChargeOrder currentOrder, ChargeStatusData statusData) {
        ChargeOrderUpdate updateData = new ChargeOrderUpdate();

        // 检查平台充电状态变化
        if (statusData.getChargeStatus() != null &&
                !statusData.getChargeStatus().equals(currentOrder.getChargeStatus())) {

            updateData.setChargeStatus(statusData.getChargeStatus());
            updateData.setChargeStatusChanged(true);

            // 根据平台状态映射订单状态
            PlatformChargeStatusEnum platformStatus = PlatformChargeStatusEnum.getByCode(statusData.getChargeStatus());
            if (platformStatus != null) {
                ChargeOrderStatusEnum newOrderStatus = platformStatus.mapToOrderStatus();
                if (newOrderStatus != null && !newOrderStatus.getCode().equals(currentOrder.getStatus())) {
                    updateData.setStatus(newOrderStatus.getCode());
                    updateData.setOrderStatusChanged(true);
                }
            }
        }

        // 检查费用变化
        if (statusData.getTotalPower() != null &&
                statusData.getTotalPower().compareTo(currentOrder.getTotalPower() != null ? currentOrder.getTotalPower() : BigDecimal.ZERO) != 0) {
            updateData.setTotalPower(statusData.getTotalPower());
            updateData.setFeeChanged(true);
        }

        if (statusData.getElectricityFee() != null &&
                statusData.getElectricityFee().compareTo(currentOrder.getElectricityFee() != null ? currentOrder.getElectricityFee() : BigDecimal.ZERO) != 0) {
            updateData.setElectricityFee(statusData.getElectricityFee());
            updateData.setFeeChanged(true);
        }

        if (statusData.getServiceFee() != null &&
                statusData.getServiceFee().compareTo(currentOrder.getServiceFee() != null ? currentOrder.getServiceFee() : BigDecimal.ZERO) != 0) {
            updateData.setServiceFee(statusData.getServiceFee());
            updateData.setFeeChanged(true);
        }

        if (statusData.getTotalFee() != null &&
                statusData.getTotalFee().compareTo(currentOrder.getTotalFee() != null ? currentOrder.getTotalFee() : BigDecimal.ZERO) != 0) {
            updateData.setTotalFee(statusData.getTotalFee());
            updateData.setFeeChanged(true);
        }

        // 检查结束时间
        if (statusData.getEndTime() != null && currentOrder.getEndTime() == null) {
            updateData.setEndTime(statusData.getEndTime());
            updateData.setTimeChanged(true);
        }

        return updateData;
    }

    /**
     * 执行订单更新
     */
    private boolean executeOrderUpdate(Long orderId, ChargeOrderUpdate updateData) {
        LambdaUpdateWrapper<ChargeOrder> updateWrapper = new LambdaUpdateWrapper<ChargeOrder>()
                .eq(ChargeOrder::getId, orderId);

        if (updateData.getStatus() != null) {
            updateWrapper.set(ChargeOrder::getStatus, updateData.getStatus());
        }
        if (updateData.getChargeStatus() != null) {
            updateWrapper.set(ChargeOrder::getChargeStatus, updateData.getChargeStatus());
        }
        if (updateData.getTotalPower() != null) {
            updateWrapper.set(ChargeOrder::getTotalPower, updateData.getTotalPower());
        }
        if (updateData.getElectricityFee() != null) {
            updateWrapper.set(ChargeOrder::getElectricityFee, updateData.getElectricityFee());
        }
        if (updateData.getServiceFee() != null) {
            updateWrapper.set(ChargeOrder::getServiceFee, updateData.getServiceFee());
        }
        if (updateData.getTotalFee() != null) {
            updateWrapper.set(ChargeOrder::getTotalFee, updateData.getTotalFee());
        }
        if (updateData.getEndTime() != null) {
            updateWrapper.set(ChargeOrder::getEndTime, updateData.getEndTime());
        }

        updateWrapper.set(ChargeOrder::getUpdateTime, LocalDateTime.now());

        int result = chargeOrderMapper.update(null, updateWrapper);
        return result > 0;
    }

    /**
     * 发送实时通知
     */
    private void sendRealtimeNotifications(ChargeOrder currentOrder, ChargeOrderUpdate updateData, ChargeStatusData statusData) {
        // 状态变化通知
        if (updateData.isChargeStatusChanged() || updateData.isOrderStatusChanged()) {
            notificationService.sendChargeStatusNotification(
                    currentOrder.getUserId(), currentOrder.getOrderNo(), statusData);
        }

        // 进度变化通知
        if (updateData.isFeeChanged() && ChargeOrderStatusEnum.CHARGING.getCode().equals(currentOrder.getStatus())) {
            Integer chargeDuration = currentOrder.getStartTime() != null ?
                    (int) java.time.Duration.between(currentOrder.getStartTime(), LocalDateTime.now()).toMinutes() : 0;

            notificationService.sendChargeProgressNotification(
                    currentOrder.getUserId(),
                    currentOrder.getOrderNo(),
                    statusData.getSoc(),
                    statusData.getTotalPower(),
                    statusData.getTotalFee(),
                    chargeDuration
            );
        }

        // 完成通知
        if (updateData.isOrderStatusChanged() && ChargeOrderStatusEnum.COMPLETED.getCode().equals(updateData.getStatus())) {
            // 需要重新查询完整订单信息
            ChargeOrder completedOrder = chargeOrderMapper.selectById(currentOrder.getId());
            notificationService.sendChargeCompleteNotification(currentOrder.getUserId(), completedOrder);
        }
    }

    /**
     * 订单更新数据类
     */
    private static class ChargeOrderUpdate {
        private Integer status;
        private Integer chargeStatus;
        private BigDecimal totalPower;
        private BigDecimal electricityFee;
        private BigDecimal serviceFee;
        private BigDecimal totalFee;
        private LocalDateTime endTime;

        private boolean orderStatusChanged = false;
        private boolean chargeStatusChanged = false;
        private boolean feeChanged = false;
        private boolean timeChanged = false;

        public boolean hasChanges() {
            return orderStatusChanged || chargeStatusChanged || feeChanged || timeChanged;
        }

        // getters and setters
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public Integer getChargeStatus() { return chargeStatus; }
        public void setChargeStatus(Integer chargeStatus) { this.chargeStatus = chargeStatus; }
        public BigDecimal getTotalPower() { return totalPower; }
        public void setTotalPower(BigDecimal totalPower) { this.totalPower = totalPower; }
        public BigDecimal getElectricityFee() { return electricityFee; }
        public void setElectricityFee(BigDecimal electricityFee) { this.electricityFee = electricityFee; }
        public BigDecimal getServiceFee() { return serviceFee; }
        public void setServiceFee(BigDecimal serviceFee) { this.serviceFee = serviceFee; }
        public BigDecimal getTotalFee() { return totalFee; }
        public void setTotalFee(BigDecimal totalFee) { this.totalFee = totalFee; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public boolean isOrderStatusChanged() { return orderStatusChanged; }
        public void setOrderStatusChanged(boolean orderStatusChanged) { this.orderStatusChanged = orderStatusChanged; }
        public boolean isChargeStatusChanged() { return chargeStatusChanged; }
        public void setChargeStatusChanged(boolean chargeStatusChanged) { this.chargeStatusChanged = chargeStatusChanged; }
        public boolean isFeeChanged() { return feeChanged; }
        public void setFeeChanged(boolean feeChanged) { this.feeChanged = feeChanged; }
        public boolean isTimeChanged() { return timeChanged; }
        public void setTimeChanged(boolean timeChanged) { this.timeChanged = timeChanged; }
    }
}
