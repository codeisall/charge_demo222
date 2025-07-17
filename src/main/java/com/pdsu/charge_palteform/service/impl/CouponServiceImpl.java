package com.pdsu.charge_palteform.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pdsu.charge_palteform.entity.CouponTemplates;
import com.pdsu.charge_palteform.entity.UserCoupon;
import com.pdsu.charge_palteform.entity.dto.AvailableCouponResponse;
import com.pdsu.charge_palteform.exception.BusinessException;
import com.pdsu.charge_palteform.mapper.CouponTemplateMapper;
import com.pdsu.charge_palteform.mapper.UserCouponMapper;
import com.pdsu.charge_palteform.service.CouponService;
import com.pdsu.charge_palteform.utils.GenerateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements CouponService {

    private final CouponTemplateMapper couponTemplateMapper;
    private final UserCouponMapper userCouponMapper;


    @Override
    @Transactional
    public void issueRegistrationCoupon(Long userId) {
        log.info("为新用户{}发放注册优惠券", userId);
        // 查找注册优惠券模板（这里假设模板ID为1是注册送的5元券）
        CouponTemplates template = couponTemplateMapper.selectOne(
                new LambdaQueryWrapper<CouponTemplates>()
                        .eq(CouponTemplates::getId, 1L) // 选择ID为1的注册优惠券模板
                        .eq(CouponTemplates::getStatus, 1)
        );
        if (template == null) {
            log.warn("注册优惠券模板不存在或已停用");
            return;
        }
        // 检查库存
        if (template.getRemainingQuantity() <= 0) {
            log.warn("注册优惠券库存不足");
            return;
        }

        // 创建用户优惠券
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponTemplateId(template.getId());
        userCoupon.setCouponCode(GenerateUtils.generateCouponCode());
        userCoupon.setStatus(1); // 未使用
        userCoupon.setReceiveTime(LocalDateTime.now());

        // 设置有效期
        if (template.getValidityType() == 1) {
            // 固定日期
            userCoupon.setValidStart(template.getStartDate().atStartOfDay());
            userCoupon.setValidEnd(template.getEndDate().atTime(23, 59, 59));
        } else {
            // 领取后生效
            userCoupon.setValidStart(LocalDateTime.now());
            userCoupon.setValidEnd(LocalDateTime.now().plusDays(template.getValidDays()));
        }
        // 保存用户优惠券
        save(userCoupon);
        // 减少模板库存
        couponTemplateMapper.update(null,
                new LambdaUpdateWrapper<CouponTemplates>()
                        .eq(CouponTemplates::getId, template.getId())
                        .setSql("remaining_quantity = remaining_quantity - 1")
        );
        log.info("用户{}注册优惠券发放成功，优惠券码：{}", userId, userCoupon.getCouponCode());
    }


    @Override
    public List<AvailableCouponResponse> getAvailableCoupons(Long userId, BigDecimal orderAmount) {
        LocalDateTime now = LocalDateTime.now();
        List<UserCoupon> availableCoupons = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getStatus, 1) // 未使用
                        .le(UserCoupon::getValidStart, now)
                        .ge(UserCoupon::getValidEnd, now)
        );

        return availableCoupons.stream()
                .map(coupon -> convertToAvailableResponse(coupon, orderAmount))
                .filter(response -> response.isCanUse())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public BigDecimal useCoupon(Long userId, Long couponId, String orderNo, BigDecimal orderAmount) {
        // 查询优惠券
        UserCoupon coupon = getOne(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getId, couponId)
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getStatus, 1)
        );
        if (coupon == null) {
            throw new BusinessException("优惠券不存在或已使用");
        }
        // 检查有效期
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getValidStart()) || now.isAfter(coupon.getValidEnd())) {
            throw new BusinessException("优惠券已过期");
        }

        // 计算抵扣金额
        BigDecimal deductionAmount = calculateCouponDeduction(coupon, orderAmount);

        if (deductionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("订单金额不满足优惠券使用条件");
        }

        // 更新优惠券状态
        coupon.setStatus(2); // 已使用
        coupon.setUseTime(now);
        coupon.setOrderNo(orderNo);
        updateById(coupon);

        log.info("优惠券使用成功，用户：{}，优惠券：{}，抵扣金额：{}", userId, coupon.getCouponCode(), deductionAmount);

        return deductionAmount;
    }

    @Override
    public BigDecimal calculateCouponDeduction(UserCoupon coupon, BigDecimal orderAmount) {
        CouponTemplates template = couponTemplateMapper.selectById(coupon.getCouponTemplateId());
        if (template == null) {
            return BigDecimal.ZERO;
        }
        // 检查最低消费金额
        if (template.getMinChargeAmount() != null &&
                orderAmount.compareTo(template.getMinChargeAmount()) < 0) {
            return BigDecimal.ZERO;
        }
        switch (template.getType()) {
            case 1: // 满减券
                return template.getValue();
            case 2: // 折扣券
                BigDecimal discount = orderAmount.multiply(template.getValue()).divide(BigDecimal.valueOf(100));
                return orderAmount.subtract(discount);
            case 3: // 现金券
                return template.getValue().min(orderAmount);
            default:
                return BigDecimal.ZERO;
        }
    }

    private AvailableCouponResponse convertToAvailableResponse(UserCoupon coupon, BigDecimal orderAmount) {
        CouponTemplates template = couponTemplateMapper.selectById(coupon.getCouponTemplateId());
        AvailableCouponResponse response = new AvailableCouponResponse();
        response.setCouponId(coupon.getId());
        response.setCouponCode(coupon.getCouponCode());
        response.setName(template.getName());
        response.setType(template.getType());
        response.setValue(template.getValue());
        response.setMinChargeAmount(template.getMinChargeAmount());
        response.setValidEnd(coupon.getValidEnd());
        // 计算抵扣金额
        BigDecimal deductionAmount = calculateCouponDeduction(coupon, orderAmount);
        response.setDeductionAmount(deductionAmount);
        response.setCanUse(deductionAmount.compareTo(BigDecimal.ZERO) > 0);
        return response;
    }



}
