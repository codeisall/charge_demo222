package com.pdsu.charge_palteform.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pdsu.charge_palteform.entity.CouponTemplates;
import com.pdsu.charge_palteform.entity.UserCoupon;
import com.pdsu.charge_palteform.entity.dto.*;
import com.pdsu.charge_palteform.exception.BusinessException;
import com.pdsu.charge_palteform.mapper.CouponTemplateMapper;
import com.pdsu.charge_palteform.mapper.UserCouponMapper;
import com.pdsu.charge_palteform.service.CouponService;
import com.pdsu.charge_palteform.utils.GenerateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
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

    @Override
    public PageResponse<UserCouponListResponse> getUserCouponList(Long userId, Integer status, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<UserCoupon> queryWrapper = new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId);

        if (status != null) {
            queryWrapper.eq(UserCoupon::getStatus, status);
        }

        queryWrapper.orderByDesc(UserCoupon::getReceiveTime);

        Page<UserCoupon> page = new Page<>(pageNum, pageSize);
        Page<UserCoupon> result = page(page, queryWrapper);

        List<UserCouponListResponse> responseList = result.getRecords().stream()
                .map(this::convertToListResponse)
                .collect(Collectors.toList());

        return PageResponse.of(responseList, result.getTotal(), pageNum, pageSize);
    }

    @Override
    public List<AvailableCouponResponse> getAvailableCoupons(Long userId, BigDecimal orderAmount, String connectorId) {
        LocalDateTime now = LocalDateTime.now();
        List<UserCoupon> availableCoupons = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getStatus, 1) // 未使用
                        .le(UserCoupon::getValidStart, now)
                        .ge(UserCoupon::getValidEnd, now)
        );
        return availableCoupons.stream()
                .filter(coupon -> checkConnectorApplicability(coupon, connectorId)) // 先过滤适用性
                .map(coupon -> convertToAvailableResponse(coupon, orderAmount)) // 再转换
                .filter(AvailableCouponResponse::isCanUse) // 最后过滤可用性
                .sorted((a, b) -> b.getDeductionAmount().compareTo(a.getDeductionAmount())) // 按抵扣金额降序
                .collect(Collectors.toList());
    }

    @Override
    public BigDecimal calculateDeduction(Long userId, CouponUsageRequest request) {
        UserCoupon coupon = getOne(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getId, request.getCouponId())
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getStatus, 1)
        );

        if (coupon == null) {
            throw new BusinessException("优惠券不存在或不可用");
        }

        // 检查有效期
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getValidStart()) || now.isAfter(coupon.getValidEnd())) {
            throw new BusinessException("优惠券已过期");
        }

        // 检查适用范围
        if (!checkConnectorApplicability(coupon, request.getConnectorId())) {
            throw new BusinessException("优惠券不适用于当前充电桩");
        }
        return calculateCouponDeduction(coupon, request.getOrderAmount());
    }

    @Override
    public CouponStatsResponse getCouponStats(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threeDaysLater = now.plusDays(3);
        // 统计各种状态的优惠券数量
        List<UserCoupon> allCoupons = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
        );
        CouponStatsResponse stats = new CouponStatsResponse();
        stats.setTotalCount(allCoupons.size());
        int availableCount = 0;
        int usedCount = 0;
        int expiredCount = 0;
        int expiringSoonCount = 0;
        BigDecimal totalSaved = BigDecimal.ZERO;

        for (UserCoupon coupon : allCoupons) {
            switch (coupon.getStatus()) {
                case 1: // 未使用
                    if (now.isAfter(coupon.getValidEnd())) {
                        expiredCount++;
                    } else {
                        availableCount++;
                        // 检查是否即将过期
                        if (coupon.getValidEnd().isBefore(threeDaysLater)) {
                            expiringSoonCount++;
                        }
                    }
                    break;
                case 2: // 已使用
                    usedCount++;
                    // 累计节省金额（这里需要从订单中获取实际抵扣金额）
                    CouponTemplates template = couponTemplateMapper.selectById(coupon.getCouponTemplateId());
                    if (template != null) {
                        totalSaved = totalSaved.add(template.getValue());
                    }
                    break;
                case 3: // 已过期
                    expiredCount++;
                    break;
            }
        }
        stats.setAvailableCount(availableCount);
        stats.setUsedCount(usedCount);
        stats.setExpiredCount(expiredCount);
        stats.setExpiringSoonCount(expiringSoonCount);
        stats.setTotalSavedAmount(totalSaved);
        // 计算当前最大可抵扣金额
        stats.setMaxAvailableDeduction(calculateMaxAvailableDeduction(userId));
        return stats;
    }

    @Override
    public String receiveCoupon(Long userId, String couponCode, Long templateId) {
        CouponTemplates template = null;

        if (templateId != null) {
            // 通过模板ID领取
            template = couponTemplateMapper.selectOne(
                    new LambdaQueryWrapper<CouponTemplates>()
                            .eq(CouponTemplates::getId, templateId)
                            .eq(CouponTemplates::getStatus, 1)
            );
        } else if (couponCode != null) {
            // 通过优惠券码领取（需要在模板表中配置）
            throw new BusinessException("优惠券码领取功能暂未实现");
        } else {
            throw new BusinessException("请提供优惠券码或模板ID");
        }

        if (template == null) {
            throw new BusinessException("优惠券不存在或已停用");
        }

        // 检查库存
        if (template.getRemainingQuantity() <= 0) {
            throw new BusinessException("优惠券已抢完");
        }

        // 检查用户是否已领取过该模板的优惠券
        long receivedCount = count(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponTemplateId, template.getId()));

        if (receivedCount > 0) {
            throw new BusinessException("您已领取过该优惠券");
        }
        // 发放优惠券
        issueUserCoupon(userId, template, "手动领取");
        return "优惠券领取成功";
    }

    @Override
    @Scheduled(cron = "0 0 2 * * ?")
    public void checkAndExpireCoupons() {
        LocalDateTime now = LocalDateTime.now();
        // 查询已过期但状态还是未使用的优惠券
        List<UserCoupon> expiredCoupons = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getStatus, 1) // 未使用
                        .lt(UserCoupon::getValidEnd, now) // 已过期
        );
        if (!expiredCoupons.isEmpty()) {
            // 批量更新为过期状态
            for (UserCoupon coupon : expiredCoupons) {
                coupon.setStatus(3); // 已过期
                updateById(coupon);
            }
            log.info("处理过期优惠券：{}张", expiredCoupons.size());
        }
    }

    @Override
    public void issueChargeCompleteCoupon(Long userId, BigDecimal chargeAmount) {
        // 根据充电金额判断发放什么优惠券
        if (chargeAmount.compareTo(BigDecimal.valueOf(50)) >= 0) {
            // 充电满50元，发放5元优惠券
            CouponTemplates template = couponTemplateMapper.selectOne(
                    new LambdaQueryWrapper<CouponTemplates>()
                            .eq(CouponTemplates::getId, 2L) // 假设ID为2的是充电完成奖励券
                            .eq(CouponTemplates::getStatus, 1)
            );
            if (template != null && template.getRemainingQuantity() > 0) {
                issueUserCoupon(userId, template, "充电完成奖励");
                log.info("用户{}充电完成，发放奖励优惠券", userId);
            }
        }
    }

    @Override
    @Transactional
    public void batchIssueCoupons(List<Long> userIds, Long templateId, String reason) {
        CouponTemplates template = couponTemplateMapper.selectById(templateId);
        if (template == null || template.getStatus() != 1) {
            throw new BusinessException("优惠券模板不存在或已停用");
        }

        if (template.getRemainingQuantity() < userIds.size()) {
            throw new BusinessException("优惠券库存不足");
        }

        int successCount = 0;
        for (Long userId : userIds) {
            try {
                issueUserCoupon(userId, template, reason);
                successCount++;
            } catch (Exception e) {
                log.error("为用户{}发放优惠券失败: {}", userId, e.getMessage());
            }
        }
        log.info("批量发放优惠券完成，成功{}个，失败{}个", successCount, userIds.size() - successCount);
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

    private UserCouponListResponse convertToListResponse(UserCoupon coupon) {
        CouponTemplates template = couponTemplateMapper.selectById(coupon.getCouponTemplateId());

        UserCouponListResponse response = new UserCouponListResponse();
        response.setCouponId(coupon.getId());
        response.setCouponCode(coupon.getCouponCode());
        response.setName(template.getName());
        response.setDescription(buildDescription(template));
        response.setType(template.getType());
        response.setTypeText(getCouponTypeText(template.getType()));
        response.setValue(template.getValue());
        response.setMinChargeAmount(template.getMinChargeAmount());
        response.setStatus(coupon.getStatus());
        response.setStatusText(getCouponStatusText(coupon.getStatus()));
        response.setValidStart(coupon.getValidStart());
        response.setValidEnd(coupon.getValidEnd());
        response.setReceiveTime(coupon.getReceiveTime());
        response.setUseTime(coupon.getUseTime());
        response.setOrderNo(coupon.getOrderNo());

        // 判断是否即将过期
        if (coupon.getStatus() == 1) {
            LocalDateTime threeDaysLater = LocalDateTime.now().plusDays(3);
            response.setExpiringSoon(coupon.getValidEnd().isBefore(threeDaysLater));
        }
        return response;
    }

    private boolean checkConnectorApplicability(UserCoupon coupon, String connectorId) {
        return true;
    }

    private BigDecimal calculateMaxAvailableDeduction(Long userId) {
        // 计算用户当前最大可抵扣金额（假设订单金额为100元）
        BigDecimal testAmount = BigDecimal.valueOf(100);
        List<AvailableCouponResponse> availableCoupons = getAvailableCoupons(userId, testAmount, null);

        return availableCoupons.stream()
                .map(AvailableCouponResponse::getDeductionAmount)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    private void issueUserCoupon(Long userId, CouponTemplates template, String reason) {
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponTemplateId(template.getId());
        userCoupon.setCouponCode(GenerateUtils.generateCouponCode());
        userCoupon.setStatus(1); // 未使用
        userCoupon.setReceiveTime(LocalDateTime.now());

        // 设置有效期
        if (template.getValidityType() == 1) {
            userCoupon.setValidStart(template.getStartDate().atStartOfDay());
            userCoupon.setValidEnd(template.getEndDate().atTime(23, 59, 59));
        } else {
            userCoupon.setValidStart(LocalDateTime.now());
            userCoupon.setValidEnd(LocalDateTime.now().plusDays(template.getValidDays()));
        }

        save(userCoupon);

        // 减少模板库存
        couponTemplateMapper.update(null,
                new LambdaUpdateWrapper<CouponTemplates>()
                        .eq(CouponTemplates::getId, template.getId())
                        .setSql("remaining_quantity = remaining_quantity - 1")
        );
        log.info("为用户{}发放优惠券成功，原因：{}", userId, reason);
    }

    private String buildDescription(CouponTemplates template) {
        StringBuilder desc = new StringBuilder();
        switch (template.getType()) {
            case 1: // 满减券
                if (template.getMinChargeAmount() != null) {
                    desc.append("满").append(template.getMinChargeAmount()).append("元");
                }
                desc.append("减").append(template.getValue()).append("元");
                break;
            case 2: // 折扣券
                desc.append(template.getValue()).append("折优惠");
                if (template.getMinChargeAmount() != null) {
                    desc.append("（满").append(template.getMinChargeAmount()).append("元可用）");
                }
                break;
            case 3: // 现金券
                desc.append("抵扣").append(template.getValue()).append("元");
                break;
        }
        return desc.toString();
    }

    private String getCouponTypeText(Integer type) {
        switch (type) {
            case 1: return "满减券";
            case 2: return "折扣券";
            case 3: return "现金券";
            default: return "未知";
        }
    }

    private String getCouponStatusText(Integer status) {
        switch (status) {
            case 1: return "未使用";
            case 2: return "已使用";
            case 3: return "已过期";
            default: return "未知";
        }
    }



}
