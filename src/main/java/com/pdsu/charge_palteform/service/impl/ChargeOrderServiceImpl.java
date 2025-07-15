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
import com.pdsu.charge_palteform.enums.ConnectorStatusEnum;
import com.pdsu.charge_palteform.exception.BusinessException;
import com.pdsu.charge_palteform.mapper.ChargeOrderMapper;
import com.pdsu.charge_palteform.service.ChargeOrderService;
import com.pdsu.charge_palteform.service.ChargingStationService;
import com.pdsu.charge_palteform.service.EnergyPlatformService;
import com.pdsu.charge_palteform.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            order.setOrderNo(generateOrderNo());
            order.setUserId(userId);
            order.setConnectorId(request.getConnectorId());
            order.setStationId(connector.getStationId());
            order.setStatus(1); // 待充电
            order.setChargeStatus(5); // 未知
            order.setTotalPower(BigDecimal.ZERO);
            order.setTotalFee(BigDecimal.ZERO);
            order.setElectricityFee(BigDecimal.ZERO);
            order.setServiceFee(BigDecimal.ZERO);

            save(order);
            log.info("创建充电订单: {}", order.getOrderNo());

            // 7. 调用电能平台启动充电
            try {
                log.info("向电能平台发送启动充电请求...");
                String platformOrderNo = energyPlatformService.startCharge(
                        order.getOrderNo(),
                        request.getConnectorId(),
                        request.getQrCode()
                );

                // 更新订单信息
                order.setPlatformOrderNo(platformOrderNo);
                order.setChargeStatus(1); // 启动中
                order.setStartTime(LocalDateTime.now());
                updateById(order);

                log.info("充电启动请求发送成功，订单号: {}, 平台订单号: {}", order.getOrderNo(), platformOrderNo);

            } catch (Exception e) {
                log.error("调用电能平台启动充电失败", e);
                // 更新订单状态为异常
                order.setStatus(6); // 异常
                updateById(order);
                throw new BusinessException("启动充电失败：" + e.getMessage());
            }

            // 8. 构建响应
            StartChargeResponse response = new StartChargeResponse();
            response.setOrderNo(order.getOrderNo());
            response.setConnectorId(request.getConnectorId());
            response.setChargeStatus(order.getChargeStatus());
            response.setStatusText(getChargeStatusText(order.getChargeStatus()));
            response.setStartTime(order.getStartTime());
            response.setMessage("充电启动请求已发送，请稍候...");

            log.info("充电启动成功，订单号: {}", order.getOrderNo());
            return response;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("启动充电异常", e);
            throw new BusinessException("启动充电失败，请重试");
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

                // 更新订单状态
                order.setChargeStatus(3); // 停止中
                updateById(order);

                log.info("停止充电请求发送成功，平台订单号: {}", order.getPlatformOrderNo());

            } catch (Exception e) {
                log.error("调用电能平台停止充电失败", e);
                throw new BusinessException("停止充电失败：" + e.getMessage());
            }

            // 3. 构建响应
            StopChargeResponse response = new StopChargeResponse();
            response.setOrderNo(order.getOrderNo());
            response.setChargeStatus(order.getChargeStatus());
            response.setStatusText(getChargeStatusText(order.getChargeStatus()));
            response.setMessage("停止充电请求已发送，请稍候...");

            log.info("充电停止请求发送成功，订单号: {}", order.getOrderNo());
            return response;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("停止充电异常", e);
            throw new BusinessException("停止充电失败，请重试");
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

    /**
     * 生成订单号
     */
    private String generateOrderNo() {
        return "CO" + System.currentTimeMillis() + String.format("%04d", (int)(Math.random() * 10000));
    }

    /**
     * 获取订单状态文本
     */
    private String getOrderStatusText(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 1: return "待充电";
            case 2: return "充电中";
            case 3: return "充电完成";
            case 4: return "已结算";
            case 5: return "已取消";
            case 6: return "异常";
            default: return "未知";
        }
    }

    /**
     * 获取充电状态文本
     */
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

    /**
     * 转换为列表项DTO
     */
    private ChargeOrderListItem convertToListItem(ChargeOrder order) {
        ChargeOrderListItem item = new ChargeOrderListItem();
        item.setOrderNo(order.getOrderNo());
        item.setStatus(order.getStatus());
        item.setStatusText(getOrderStatusText(order.getStatus()));
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
        detail.setStatusText(getOrderStatusText(order.getStatus()));
        detail.setChargeStatus(order.getChargeStatus());
        detail.setChargeStatusText(getChargeStatusText(order.getChargeStatus()));
        detail.setStopReason(order.getStopReason());
        // detail.setStopReasonText(getStopReasonText(order.getStopReason()));
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
            // detail.setConnectorTypeText(ConnectorTypeEnum.getDesc(connector.getConnectorType()));

        } catch (Exception e) {
            log.warn("获取充电站信息失败: {}", e.getMessage());
        }

        // TODO: 获取充电明细
        // detail.setChargeDetails(getChargeDetails(order.getId()));

        return detail;
    }
}
