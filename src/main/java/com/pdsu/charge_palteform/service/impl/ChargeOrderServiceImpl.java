package com.pdsu.charge_palteform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pdsu.charge_palteform.entity.ChargeOrder;
import com.pdsu.charge_palteform.entity.ChargingConnector;
import com.pdsu.charge_palteform.entity.ChargingStation;
import com.pdsu.charge_palteform.entity.User;
import com.pdsu.charge_palteform.entity.dto.*;
import com.pdsu.charge_palteform.entity.platefrom.charge.ChargePolicyInfo;
import com.pdsu.charge_palteform.entity.platefrom.charge.ChargeStatusData;
import com.pdsu.charge_palteform.enums.ChargeOrderStatusEnum;
import com.pdsu.charge_palteform.enums.ConnectorStatusEnum;
import com.pdsu.charge_palteform.enums.ConnectorTypeEnum;
import com.pdsu.charge_palteform.enums.PlatformChargeStatusEnum;
import com.pdsu.charge_palteform.exception.BusinessException;
import com.pdsu.charge_palteform.mapper.ChargeOrderMapper;
import com.pdsu.charge_palteform.service.*;
import com.pdsu.charge_palteform.utils.GenerateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;




@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeOrderServiceImpl  extends ServiceImpl<ChargeOrderMapper, ChargeOrder> implements ChargeOrderService {

    private final ChargingStationService stationService;
    private final UserService userService;
    private final EnergyPlatformService energyPlatformService;
    private final CouponService couponService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public StartChargeResponse startCharge(Long userId, StartChargeRequest request) {
        log.info("用户{}请求启动充电，充电桩ID: {}", userId, request.getConnectorId());

        try {
            // 1. 验证用户
            User user = userService.getUserById(userId);
            if (user.getStatus() != 1) {
                throw new BusinessException("用户状态异常，无法充电");
            }
            // 2. 验证充电桩
            ChargingConnector connector = stationService.getConnectorById(request.getConnectorId());
            if (connector.getStatus() != 1) { // 1-空闲
                throw new BusinessException("充电桩当前不可用，状态：" + ConnectorStatusEnum.getDesc(connector.getStatus()));
            }
            // 3. 检查用户是否有进行中的订单
            ChargeOrder existingOrder = getOne(new LambdaQueryWrapper<ChargeOrder>()
                    .eq(ChargeOrder::getUserId, userId)
                    .in(ChargeOrder::getStatus, 1, 2) // 1-待充电，2-充电中
                    .orderByDesc(ChargeOrder::getCreateTime)
                    .last("LIMIT 1"));
            if (existingOrder != null) {
                throw new BusinessException("您有正在进行的充电订单，请先完成当前充电");
            }
            // 4. 设备认证
            log.info("开始设备认证...");
            boolean authResult = energyPlatformService.authenticateConnector(request.getConnectorId());
            if (!authResult) {
                throw new BusinessException("设备认证失败，请检查充电桩状态");
            }
            log.info("设备认证成功");

            // 5. 查询充电策略
            log.info("查询充电策略...");
            ChargePolicyInfo policyInfo = energyPlatformService.getChargePolicy(request.getConnectorId());
            log.info("获取到充电策略: {}", policyInfo);

            // 6. 生成订单
            ChargeOrder order = new ChargeOrder();
            order.setOrderNo(GenerateUtils.generateOrderNo());
            order.setUserId(userId);
            order.setConnectorId(request.getConnectorId());
            order.setStationId(connector.getStationId());
            order.setStatus(1); // 待充电
            order.setChargeStatus(5); // 未知
            order.setTotalPower(BigDecimal.ZERO);
            order.setTotalFee(BigDecimal.ZERO);
            order.setElectricityFee(BigDecimal.ZERO);
            order.setServiceFee(BigDecimal.ZERO);

            // 先保存订单
            boolean saveResult = save(order);
            if (!saveResult) {
                throw new BusinessException("创建订单失败");
            }
            log.info("创建充电订单成功: {}, ID: {}", order.getOrderNo(), order.getId());

            // 7. 调用电能平台启动充电
            String platformOrderNo = null;
            try {
                log.info("向电能平台发送启动充电请求...");
                platformOrderNo = energyPlatformService.startCharge(
                        order.getOrderNo(),
                        request.getConnectorId(),
                        request.getQrCode()
                );

                if (platformOrderNo == null || platformOrderNo.trim().isEmpty()) {
                    throw new BusinessException("平台返回的订单号为空");
                }

                log.info("充电启动请求发送成功，订单号: {}, 平台订单号: {}", order.getOrderNo(), platformOrderNo);
            } catch (Exception e) {
                log.error("调用电能平台启动充电失败", e);
                // 更新订单状态为异常
                order.setStatus(6); // 异常
                order.setChargeStatus(5); // 未知
                boolean updateResult = updateById(order);
                log.info("订单状态更新为异常: {}, 更新结果: {}", order.getOrderNo(), updateResult);
                throw new BusinessException("启动充电失败：" + e.getMessage());
            }

            // 8. 更新订单信息
            order.setPlatformOrderNo(platformOrderNo);
            order.setChargeStatus(1); // 启动中
            order.setStatus(2); // 充电中
            order.setStartTime(LocalDateTime.now());

            // 强制更新数据库
            boolean updateResult = updateById(order);
            log.info("订单状态更新结果: {}, orderNo: {}, status: {}, chargeStatus: {}",
                    updateResult, order.getOrderNo(), order.getStatus(), order.getChargeStatus());

            if (!updateResult) {
                log.error("订单状态更新失败，订单号: {}", order.getOrderNo());
                throw new BusinessException("订单状态更新失败");
            }

            //发送实时启动通知
            ChargingStation station = stationService.getById(connector.getStationId());
            notificationService.sendChargeStartNotification(
                    userId, order.getOrderNo(), station.getStationName(), request.getConnectorId());

            // 9. 立即从平台查询一次状态进行同步
            try {
                log.info("立即同步平台状态...");
                asyncSyncOrderStatus(order.getOrderNo(), platformOrderNo);
            } catch (Exception e) {
                log.warn("立即同步状态失败，将由定时任务处理: {}", e.getMessage());
            }

            // 10. 构建响应
            StartChargeResponse response = new StartChargeResponse();
            response.setOrderNo(order.getOrderNo());
            response.setConnectorId(request.getConnectorId());
            response.setChargeStatus(order.getChargeStatus());
            response.setStatusText(getChargeStatusText(order.getChargeStatus()));
            response.setStartTime(order.getStartTime());
            response.setMessage("充电启动成功，正在启动中...");

            log.info("充电启动成功，订单号: {}, 最终状态: status={}, chargeStatus={}",
                    order.getOrderNo(), order.getStatus(), order.getChargeStatus());
            return response;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("启动充电异常", e);
            throw new BusinessException("启动充电失败，请重试");
        }
    }

    @Async
    public void asyncSyncOrderStatus(String orderNo, String platformOrderNo) {
        try {
            // 等待3秒让平台处理
            Thread.sleep(3000);

            log.info("开始异步同步订单状态: {}", orderNo);
            syncOrderStatusFromPlatform(orderNo, platformOrderNo);

        } catch (Exception e) {
            log.error("异步同步订单状态失败: {}", e.getMessage());
        }
    }




    @Override
    @Transactional
    public StopChargeResponse stopCharge(Long userId, StopChargeRequest request) {
        log.info("用户{}请求停止充电，订单号: {}", userId, request.getOrderNo());

        try {
            // 1. 查询订单
            ChargeOrder order = getOne(new LambdaQueryWrapper<ChargeOrder>()
                    .eq(ChargeOrder::getOrderNo, request.getOrderNo())
                    .eq(ChargeOrder::getUserId, userId));

            if (order == null) {
                throw new BusinessException("订单不存在");
            }

            if (order.getStatus() != 2) { // 2-充电中
                throw new BusinessException("订单状态异常，当前状态：" + getOrderStatusText(order.getStatus()));
            }

            if (order.getPlatformOrderNo() == null) {
                throw new BusinessException("平台订单号为空，无法停止充电");
            }

            // 2. 调用电能平台停止充电
            try {
                log.info("向电能平台发送停止充电请求...");
                boolean stopResult = energyPlatformService.stopCharge(
                        order.getPlatformOrderNo(),
                        order.getConnectorId()
                );

                if (!stopResult) {
                    throw new BusinessException("电能平台停止充电失败");
                }
                log.info("停止充电请求发送成功，平台订单号: {}", order.getPlatformOrderNo());
            } catch (Exception e) {
                log.error("调用电能平台停止充电失败", e);
                throw new BusinessException("停止充电失败：" + e.getMessage());
            }
            // 3. 更新订单状态
            order.setChargeStatus(3);
            order.setStopReason(0);
            order.setEndTime(LocalDateTime.now()); // 设置结束时间
            // 立即更新数据库
            boolean updateResult = updateById(order);
            if (!updateResult) {
                log.error("更新订单状态失败，订单号: {}", order.getOrderNo());
                throw new BusinessException("更新订单状态失败");
            }
            log.info("订单状态已更新: orderNo={}, status={}, chargeStatus={}, stopReason={}",
                    order.getOrderNo(), order.getStatus(), order.getChargeStatus(), order.getStopReason());

            notificationService.sendChargeStatusNotification(
                    userId, order.getOrderNo(), convertOrderToStatusData(order));

            // 4. 异步查询最终状态
            asyncQueryFinalStatus(order.getOrderNo(), order.getPlatformOrderNo());
            // 5. 构建响应
            StopChargeResponse response = new StopChargeResponse();
            response.setOrderNo(order.getOrderNo());
            response.setChargeStatus(order.getChargeStatus());
            response.setStatusText(getChargeStatusText(order.getChargeStatus()));
            response.setEndTime(order.getEndTime());
            response.setMessage("停止充电请求已发送，正在处理中...");

            log.info("充电停止请求发送成功，订单号: {}", order.getOrderNo());
            return response;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("停止充电异常", e);
            throw new BusinessException("停止充电失败，请重试");
        }
    }

    @Async("taskExecutor")
    public void asyncQueryFinalStatus(String orderNo, String platformOrderNo) {
        try {
            // 等待5秒让平台处理停止请求
            Thread.sleep(5000);

            log.info("开始查询订单最终状态: {}", orderNo);

            // 查询最终状态
            ChargeStatusData statusData = energyPlatformService.queryChargeStatus(platformOrderNo);

            if (statusData != null) {
                ChargeOrder order = getOne(new LambdaQueryWrapper<ChargeOrder>()
                        .eq(ChargeOrder::getOrderNo, orderNo));

                if (order != null) {
                    boolean needUpdate = false;
                    ChargeOrder updateOrder = new ChargeOrder();
                    updateOrder.setId(order.getId());

                    // 如果平台状态是已结束，更新为充电完成
                    if (statusData.getChargeStatus() != null && statusData.getChargeStatus() == 4) {
                        updateOrder.setStatus(3); // 充电完成
                        updateOrder.setChargeStatus(4); // 已结束
                        needUpdate = true;
                        log.info("订单{}状态更新为充电完成", orderNo);
                    }

                    // 更新最终的费用信息
                    if (statusData.getTotalPower() != null) {
                        updateOrder.setTotalPower(statusData.getTotalPower());
                        needUpdate = true;
                    }
                    if (statusData.getElectricityFee() != null) {
                        updateOrder.setElectricityFee(statusData.getElectricityFee());
                        needUpdate = true;
                    }
                    if (statusData.getServiceFee() != null) {
                        updateOrder.setServiceFee(statusData.getServiceFee());
                        needUpdate = true;
                    }
                    if (statusData.getTotalFee() != null) {
                        updateOrder.setTotalFee(statusData.getTotalFee());
                        needUpdate = true;
                    }

                    // 更新最终结束时间
                    if (statusData.getEndTime() != null) {
                        updateOrder.setEndTime(statusData.getEndTime());
                        needUpdate = true;
                    }

                    if (needUpdate) {
                        updateById(updateOrder);
                        log.info("订单{}最终状态更新完成", orderNo);
                    }
                }
            }

        } catch (Exception e) {
            log.error("查询订单最终状态失败: {}", e.getMessage());
        }
    }


    @Override
    public ChargeStatusResponse getChargeStatus(Long userId, String orderNo) {
        // 1. 查询本地订单
        ChargeOrder order = getOne(new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getOrderNo, orderNo)
                .eq(ChargeOrder::getUserId, userId));
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 2. 查询充电桩和充电站信息
        ChargingConnector connector = stationService.getConnectorById(order.getConnectorId());
        ChargingStation station = stationService.getById(connector.getStationId());

        // 3. 如果订单在充电中，从电能平台获取最新状态
        if (order.getStatus() == 2 && order.getPlatformOrderNo() != null) { // 充电中
            try {
                log.info("从电能平台查询最新充电状态...");
                ChargeStatusData statusData = energyPlatformService.queryChargeStatus(order.getPlatformOrderNo());
                log.info("statusData: {}", statusData);
                // 更新本地订单数据
                updateOrderFromPlatformStatus(order, statusData);
            } catch (Exception e) {
                log.warn("查询电能平台充电状态失败: {}", e.getMessage());
                // 查询失败不影响返回本地数据
            }
        }

        // 4. 构建响应
        ChargeStatusResponse response = new ChargeStatusResponse();
        response.setOrderNo(order.getOrderNo());
        response.setConnectorId(order.getConnectorId());
        response.setStationName(station.getStationName());
        response.setConnectorName(connector.getConnectorName());
        response.setOrderStatus(order.getStatus());
        response.setOrderStatusText(getOrderStatusText(order.getStatus()));
        response.setChargeStatus(order.getChargeStatus());
        response.setChargeStatusText(getChargeStatusText(order.getChargeStatus()));
        response.setStartTime(order.getStartTime());
        response.setCurrentTime(LocalDateTime.now());

        // 计算充电时长
        if (order.getStartTime() != null) {
            LocalDateTime endTime = order.getEndTime() != null ? order.getEndTime() : LocalDateTime.now();
            response.setChargeDuration((int) ChronoUnit.MINUTES.between(order.getStartTime(), endTime));
        }

        response.setCurrentPower(connector.getCurrentPower());
        response.setTotalPower(order.getTotalPower());
        response.setElectricityFee(order.getElectricityFee());
        response.setServiceFee(order.getServiceFee());
        response.setTotalFee(order.getTotalFee());

        response.setElectricityPrice(connector.getElectricityFee());
        response.setServicePrice(connector.getServiceFee());

        return response;
    }

    /**
     * 根据电能平台状态数据更新本地订单
     */
    private void updateOrderFromPlatformStatus(ChargeOrder order, ChargeStatusData statusData) {
        try {
            boolean needUpdate = false;

            // 更新充电状态
            if (statusData.getChargeStatus() != null && !statusData.getChargeStatus().equals(order.getChargeStatus())) {
                order.setChargeStatus(statusData.getChargeStatus());
                needUpdate = true;
            }

            // 更新费用信息
            if (statusData.getTotalPower() != null) {
                order.setTotalPower(statusData.getTotalPower());
                needUpdate = true;
            }
            if (statusData.getElectricityFee() != null) {
                order.setElectricityFee(statusData.getElectricityFee());
                needUpdate = true;
            }
            if (statusData.getServiceFee() != null) {
                order.setServiceFee(statusData.getServiceFee());
                needUpdate = true;
            }
            if (statusData.getTotalFee() != null) {
                order.setTotalFee(statusData.getTotalFee());
                needUpdate = true;
            }

            // 更新结束时间
            if (statusData.getEndTime() != null && order.getEndTime() == null) {
                order.setEndTime(statusData.getEndTime());
                needUpdate = true;
            }

            // 根据充电状态更新订单状态
            if (statusData.getChargeStatus() != null) {
                if (statusData.getChargeStatus() == 4 && order.getStatus() == 2) { // 已结束
                    order.setStatus(3); // 充电完成
                    needUpdate = true;
                }
            }

            if (needUpdate) {
                updateById(order);
                log.debug("订单{}状态已从电能平台同步更新", order.getOrderNo());
            }

        } catch (Exception e) {
            log.error("更新订单状态失败", e);
        }
    }

    @Override
    public ChargeStatusResponse getCurrentChargeOrder(Long userId) {
        ChargeOrder order = getOne(new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getUserId, userId)
                .in(ChargeOrder::getStatus, 1, 2) // 1-待充电，2-充电中
                .orderByDesc(ChargeOrder::getCreateTime)
                .last("LIMIT 1"));

        if (order == null) {
            return null;
        }

        return getChargeStatus(userId, order.getOrderNo());
    }

    @Override
    public PageResponse<ChargeOrderListItem> getUserChargeOrders(Long userId, ChargeOrderQueryRequest request) {
        LambdaQueryWrapper<ChargeOrder> queryWrapper = new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getUserId, userId);

        // 状态筛选
        if (request.getStatus() != null) {
            queryWrapper.eq(ChargeOrder::getStatus, request.getStatus());
        }

        // 日期筛选
        if (request.getStartDate() != null) {
            queryWrapper.ge(ChargeOrder::getCreateTime, request.getStartDate());
        }
        if (request.getEndDate() != null) {
            queryWrapper.le(ChargeOrder::getCreateTime, request.getEndDate());
        }

        queryWrapper.orderByDesc(ChargeOrder::getCreateTime);

        Page<ChargeOrder> page = new Page<>(request.getPageNum(), request.getPageSize());
        Page<ChargeOrder> result = page(page, queryWrapper);

        // 转换为DTO
        List<ChargeOrderListItem> items = result.getRecords().stream()
                .map(this::convertToListItem)
                .collect(Collectors.toList());

        return PageResponse.of(items, result.getTotal(), request.getPageNum(), request.getPageSize());
    }

    @Override
    public ChargeOrderDetail getOrderDetail(Long userId, String orderNo) {
        ChargeOrder order = getOne(new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getOrderNo, orderNo)
                .eq(ChargeOrder::getUserId, userId));

        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        return convertToDetail(order);
    }

    @Override
    public void handleChargeStatusPush(String orderNo, Object statusData) {
        // 处理电能平台推送的充电状态
        log.info("收到充电状态推送，订单号: {}", orderNo);
        // 实现状态更新逻辑
    }

    @Override
    public void handleChargeCompletePush(String orderNo, Object orderData) {
        // 处理电能平台推送的充电完成信息
        log.info("收到充电完成推送，订单号: {}", orderNo);
        // 实现订单完成逻辑
    }

    @Override
    public ChargeOrder getOne(LambdaQueryWrapper<ChargeOrder> queryWrapper) {
        return baseMapper.selectOne(queryWrapper);
    }

    @Override
    public ChargeOrder getByPlatformOrderNo(String platformOrderNo) {
        return baseMapper.selectOne(
                new LambdaQueryWrapper<ChargeOrder>()
                        .eq(ChargeOrder::getPlatformOrderNo, platformOrderNo)
        );
    }

    @Override
    public boolean updateById(ChargeOrder order) {
        return baseMapper.updateById(order) > 0;
    }


    /**
     * 获取订单状态文本
     */
    private String getOrderStatusText(Integer status) {
        return ChargeOrderStatusEnum.getDesc(status);
    }

    /**
     * 获取充电状态文本
     */
    private String getChargeStatusText(Integer chargeStatus) {
        return PlatformChargeStatusEnum.getDesc(chargeStatus);
    }

    /**
     * 转换为列表项DTO
     */
    private ChargeOrderListItem convertToListItem(ChargeOrder order) {
        ChargeOrderListItem item = new ChargeOrderListItem();
        item.setOrderNo(order.getOrderNo());
        item.setStatus(order.getStatus());
        item.setStatusText(ChargeOrderStatusEnum.getDesc(order.getStatus()));
        item.setStartTime(order.getStartTime());
        item.setEndTime(order.getEndTime());
        item.setTotalPower(order.getTotalPower());
        item.setTotalFee(order.getTotalFee());

        // 计算充电时长
        if (order.getStartTime() != null) {
            LocalDateTime endTime = order.getEndTime() != null ? order.getEndTime() : LocalDateTime.now();
            item.setChargeDuration((int) ChronoUnit.MINUTES.between(order.getStartTime(), endTime));
        }

        // 获取充电站和充电桩名称
        try {
            ChargingConnector connector = stationService.getConnectorById(order.getConnectorId());
            ChargingStation station = stationService.getById(connector.getStationId());
            item.setStationName(station.getStationName());
            item.setConnectorName(connector.getConnectorName());
        } catch (Exception e) {
            log.warn("获取充电站信息失败: {}", e.getMessage());
            item.setStationName("未知充电站");
            item.setConnectorName("未知充电桩");
        }
        return item;
    }

    /**
     * 转换为详情DTO
     */
    private ChargeOrderDetail convertToDetail(ChargeOrder order) {
        ChargeOrderDetail detail = new ChargeOrderDetail();
        detail.setOrderNo(order.getOrderNo());
        detail.setStatus(order.getStatus());
        detail.setStatusText(ChargeOrderStatusEnum.getDesc(order.getStatus()));
        detail.setChargeStatus(order.getChargeStatus());
        detail.setChargeStatusText(PlatformChargeStatusEnum.getDesc(order.getChargeStatus())); // 使用枚举
        detail.setStopReason(order.getStopReason());
        detail.setStopReasonText(getStopReasonText(order.getStopReason()));
        detail.setStartTime(order.getStartTime());
        detail.setEndTime(order.getEndTime());
        detail.setTotalPower(order.getTotalPower());
        detail.setElectricityFee(order.getElectricityFee());
        detail.setServiceFee(order.getServiceFee());
        detail.setTotalFee(order.getTotalFee());

        // 计算充电时长
        if (order.getStartTime() != null) {
            LocalDateTime endTime = order.getEndTime() != null ? order.getEndTime() : LocalDateTime.now();
            detail.setChargeDuration((int) ChronoUnit.MINUTES.between(order.getStartTime(), endTime));
        }

        // 获取充电站和充电桩信息
        try {
            ChargingConnector connector = stationService.getConnectorById(order.getConnectorId());
            ChargingStation station = stationService.getById(connector.getStationId());

            detail.setStationId(station.getStationId());
            detail.setStationName(station.getStationName());
            detail.setStationAddress(station.getAddress());
            detail.setConnectorId(connector.getConnectorId());
            detail.setConnectorName(connector.getConnectorName());
            detail.setConnectorType(connector.getConnectorType());
            detail.setConnectorTypeText(ConnectorTypeEnum.getDesc(connector.getConnectorType()));

        } catch (Exception e) {
            log.warn("获取充电站信息失败: {}", e.getMessage());
        }
        return detail;
    }

    public void syncOrderStatusFromPlatform(String orderNo, String platformOrderNo) {
        try {
            log.info("从平台查询订单状态: orderNo={}, platformOrderNo={}", orderNo, platformOrderNo);

            ChargeStatusData statusData = energyPlatformService.queryChargeStatus(platformOrderNo);
            if (statusData == null) {
                log.warn("未获取到平台状态数据: {}", platformOrderNo);
                return;
            }
            log.info("获取到平台状态数据: chargeStatus={}, totalPower={}, totalFee={}",
                    statusData.getChargeStatus(), statusData.getTotalPower(), statusData.getTotalFee());

            ChargeOrder order = getOne(new LambdaQueryWrapper<ChargeOrder>()
                    .eq(ChargeOrder::getOrderNo, orderNo));

            if (order == null) {
                log.warn("本地订单不存在: {}", orderNo);
                return;
            }

            boolean needUpdate = false;
            ChargeOrder updateOrder = new ChargeOrder();
            updateOrder.setId(order.getId());

            // 更新充电状态
            if (statusData.getChargeStatus() != null && !statusData.getChargeStatus().equals(order.getChargeStatus())) {
                updateOrder.setChargeStatus(statusData.getChargeStatus());
                needUpdate = true;
                log.info("更新充电状态: {} -> {}", order.getChargeStatus(), statusData.getChargeStatus());
            }

            // 根据充电状态更新订单状态
            Integer newOrderStatus = mapChargeStatusToOrderStatus(statusData.getChargeStatus());
            if (newOrderStatus != null && !newOrderStatus.equals(order.getStatus())) {
                updateOrder.setStatus(newOrderStatus);
                needUpdate = true;
                log.info("更新订单状态: {} -> {}", order.getStatus(), newOrderStatus);
            }

            // 更新费用信息
            if (statusData.getTotalPower() != null && statusData.getTotalPower().compareTo(order.getTotalPower() != null ? order.getTotalPower() : BigDecimal.ZERO) != 0) {
                updateOrder.setTotalPower(statusData.getTotalPower());
                needUpdate = true;
                log.info("更新充电量: {} -> {}", order.getTotalPower(), statusData.getTotalPower());
            }

            if (statusData.getElectricityFee() != null && statusData.getElectricityFee().compareTo(order.getElectricityFee() != null ? order.getElectricityFee() : BigDecimal.ZERO) != 0) {
                updateOrder.setElectricityFee(statusData.getElectricityFee());
                needUpdate = true;
            }

            if (statusData.getServiceFee() != null && statusData.getServiceFee().compareTo(order.getServiceFee() != null ? order.getServiceFee() : BigDecimal.ZERO) != 0) {
                updateOrder.setServiceFee(statusData.getServiceFee());
                needUpdate = true;
            }

            if (statusData.getTotalFee() != null && statusData.getTotalFee().compareTo(order.getTotalFee() != null ? order.getTotalFee() : BigDecimal.ZERO) != 0) {
                updateOrder.setTotalFee(statusData.getTotalFee());
                needUpdate = true;
                log.info("更新总费用: {} -> {}", order.getTotalFee(), statusData.getTotalFee());
            }

            // 更新结束时间
            if (statusData.getEndTime() != null && order.getEndTime() == null) {
                updateOrder.setEndTime(statusData.getEndTime());
                needUpdate = true;
                log.info("设置结束时间: {}", statusData.getEndTime());
            }

            if (needUpdate) {
                boolean updateResult = updateById(updateOrder);
                log.info("订单状态同步更新: orderNo={}, updateResult={}", orderNo, updateResult);
            } else {
                log.debug("订单状态无变化，无需更新: {}", orderNo);
            }

        } catch (Exception e) {
            log.error("同步订单状态异常: orderNo={}, error={}", orderNo, e.getMessage(), e);
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
            case 3: return 2; // 停止中 -> 充电中（保持充电中状态）
            case 4: return 3; // 已结束 -> 充电完成
            case 5: return null; // 未知 -> 不变更
            default: return null;
        }
    }

    private String getStopReasonText(Integer stopReason) {
        if (stopReason == null) return "未知";
        switch (stopReason) {
            case 0: return "用户手动停止";
            case 1: return "平台停止";
            case 2: return "BMS停止";
            case 3: return "设备故障";
            case 4: return "连接器断开";
            default: return "未知原因";
        }
    }

    private Integer mapPlatformStopReason(Integer platformStopReason) {
        if (platformStopReason == null) return null;

        switch (platformStopReason) {
            case 0: return 0; // 用户手动停止
            case 1: return 1; // 平台停止
            case 2: return 2; // BMS停止
            case 3: return 3; // 设备故障
            case 4: return 4; // 连接器断开
            default: return 1; // 默认为平台停止
        }
    }

    private ChargeStatusData convertOrderToStatusData(ChargeOrder order) {
        ChargeStatusData statusData = new ChargeStatusData();
        statusData.setPlatformOrderNo(order.getPlatformOrderNo());
        statusData.setConnectorId(order.getConnectorId());
        statusData.setChargeStatus(order.getChargeStatus());
        statusData.setStartTime(order.getStartTime());
        statusData.setEndTime(order.getEndTime());
        statusData.setTotalPower(order.getTotalPower());
        statusData.setElectricityFee(order.getElectricityFee());
        statusData.setServiceFee(order.getServiceFee());
        statusData.setTotalFee(order.getTotalFee());
        return statusData;
    }

}
