package com.pdsu.charge_palteform.service;

import com.pdsu.charge_palteform.entity.UserCoupon;
import com.pdsu.charge_palteform.entity.dto.*;

import java.math.BigDecimal;
import java.util.List;

public interface CouponService {
    /**
     * 为新用户发放注册优惠券
     */
    void issueRegistrationCoupon(Long userId);

    /**
     * 获取用户可用优惠券
     */
    List<AvailableCouponResponse> getAvailableCoupons(Long userId, BigDecimal orderAmount);

    /**
     * 使用优惠券
     */
    BigDecimal useCoupon(Long userId, Long couponId, String orderNo, BigDecimal orderAmount);

    /**
     * 计算优惠券抵扣金额
     */
    BigDecimal calculateCouponDeduction(UserCoupon coupon, BigDecimal orderAmount);


    // 新增方法
    /**
     * 获取用户优惠券列表（分页）
     */
    PageResponse<UserCouponListResponse> getUserCouponList(Long userId, Integer status, Integer pageNum, Integer pageSize);

    /**
     * 获取可用优惠券（支持充电桩筛选）
     */
    List<AvailableCouponResponse> getAvailableCoupons(Long userId, BigDecimal orderAmount, String connectorId);

    /**
     * 计算优惠券抵扣金额
     */
    BigDecimal calculateDeduction(Long userId, CouponUsageRequest request);

    /**
     * 获取用户优惠券统计
     */
    CouponStatsResponse getCouponStats(Long userId);

    /**
     * 手动领取优惠券
     */
    String receiveCoupon(Long userId, String couponCode, Long templateId);

    /**
     * 检查并处理过期优惠券
     */
    void checkAndExpireCoupons();

    /**
     * 为充电完成用户发放优惠券
     */
    void issueChargeCompleteCoupon(Long userId, BigDecimal chargeAmount);

    /**
     * 批量发放优惠券（活动使用）
     */
    void batchIssueCoupons(List<Long> userIds, Long templateId, String reason);

}
