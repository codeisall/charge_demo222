package com.pdsu.charge_palteform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pdsu.charge_palteform.entity.ChargeOrder;
import com.pdsu.charge_palteform.entity.dto.*;

public interface ChargeOrderService {
    /**
     * 启动充电
     * @param userId 用户ID
     * @param request 启动充电请求
     * @return 启动结果
     */
    StartChargeResponse startCharge(Long userId, StartChargeRequest request);

    /**
     * 停止充电
     * @param userId 用户ID
     * @param request 停止充电请求
     * @return 停止结果
     */
    StopChargeResponse stopCharge(Long userId, StopChargeRequest request);

    /**
     * 查询充电状态
     * @param userId 用户ID
     * @param orderNo 订单号
     * @return 充电状态
     */
    ChargeStatusResponse getChargeStatus(Long userId, String orderNo);

    /**
     * 获取用户当前进行中的充电订单
     * @param userId 用户ID
     * @return 当前充电订单，如果没有则返回null
     */
    ChargeStatusResponse getCurrentChargeOrder(Long userId);

    /**
     * 分页查询用户充电订单
     * @param userId 用户ID
     * @param request 查询条件
     * @return 订单列表
     */
    PageResponse<ChargeOrderListItem> getUserChargeOrders(Long userId, ChargeOrderQueryRequest request);

    /**
     * 获取充电订单详情
     * @param userId 用户ID
     * @param orderNo 订单号
     * @return 订单详情
     */
    ChargeOrderDetail getOrderDetail(Long userId, String orderNo);

    /**
     * 处理充电状态推送（来自电能平台）
     * @param orderNo 订单号
     * @param statusData 状态数据
     */
    void handleChargeStatusPush(String orderNo, Object statusData);

    /**
     * 处理充电完成推送（来自电能平台）
     * @param orderNo 订单号
     * @param orderData 订单数据
     */
    void handleChargeCompletePush(String orderNo, Object orderData);

    ChargeOrder getOne(LambdaQueryWrapper<ChargeOrder> queryWrapper);

    boolean updateById(ChargeOrder order);

    ChargeOrder getByPlatformOrderNo(String platformOrderNo);
}
