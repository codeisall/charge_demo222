package com.pdsu.charge_palteform.controller;

import com.pdsu.charge_palteform.common.Result;
import com.pdsu.charge_palteform.entity.dto.*;
import com.pdsu.charge_palteform.service.CouponService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "优惠券管理", description = "优惠券相关接口")
@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @Operation(summary = "获取用户优惠券列表", description = "获取用户的所有优惠券，包括未使用、已使用、已过期")
    @GetMapping("/list")
    public Result<PageResponse<UserCouponListResponse>> getUserCoupons(
            HttpServletRequest request,
            @Parameter(description = "状态筛选：1-未使用，2-已使用，3-已过期") @RequestParam(required = false) Integer status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "页面大小") @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = (Long) request.getAttribute("userId");
        PageResponse<UserCouponListResponse> response = couponService.getUserCouponList(userId, status, pageNum, pageSize);
        return Result.success(response);
    }

    @Operation(summary = "获取可用优惠券", description = "根据订单金额获取当前可使用的优惠券")
    @GetMapping("/available")
    public Result<List<AvailableCouponResponse>> getAvailableCoupons(
            HttpServletRequest request,
            @Parameter(description = "订单金额") @RequestParam BigDecimal orderAmount,
            @Parameter(description = "充电桩ID") @RequestParam(required = false) String connectorId) {
        Long userId = (Long) request.getAttribute("userId");
        List<AvailableCouponResponse> coupons = couponService.getAvailableCoupons(userId, orderAmount, connectorId);
        return Result.success(coupons);
    }

    @Operation(summary = "计算优惠券抵扣", description = "计算指定优惠券对订单的抵扣金额")
    @PostMapping("/calculate")
    public Result<BigDecimal> calculateCouponDeduction(
            HttpServletRequest request,
            @Valid @RequestBody CouponUsageRequest usageRequest) {
        Long userId = (Long) request.getAttribute("userId");
        BigDecimal deduction = couponService.calculateDeduction(userId, usageRequest);
        return Result.success(deduction);
    }

    @Operation(summary = "获取优惠券统计", description = "获取用户优惠券的统计信息")
    @GetMapping("/stats")
    public Result<CouponStatsResponse> getCouponStats(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        CouponStatsResponse stats = couponService.getCouponStats(userId);
        return Result.success(stats);
    }

    @Operation(summary = "手动领取优惠券", description = "通过优惠券码或活动领取优惠券")
    @PostMapping("/receive")
    public Result<String> receiveCoupon(
            HttpServletRequest request,
            @RequestParam(required = false) String couponCode,
            @RequestParam(required = false) Long templateId) {
        Long userId = (Long) request.getAttribute("userId");
        String result = couponService.receiveCoupon(userId, couponCode, templateId);
        return Result.success(result);
    }
}
