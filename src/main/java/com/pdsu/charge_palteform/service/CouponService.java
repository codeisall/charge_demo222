package com.pdsu.charge_palteform.service;

import com.pdsu.charge_palteform.entity.UserCoupon;
import com.pdsu.charge_palteform.entity.dto.AvailableCouponResponse;

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
}
