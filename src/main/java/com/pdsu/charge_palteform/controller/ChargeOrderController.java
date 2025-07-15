package com.pdsu.charge_palteform.controller;


import com.pdsu.charge_palteform.common.Result;
import com.pdsu.charge_palteform.entity.dto.*;
import com.pdsu.charge_palteform.service.ChargeOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "充电订单管理", description = "充电订单相关接口")
@RestController
@RequestMapping("/api/charge")
@RequiredArgsConstructor
public class ChargeOrderController {

    private final ChargeOrderService chargeOrderService;

    @Operation(summary = "启动充电", description = "用户启动充电")
    @PostMapping("/start")
    public Result<StartChargeResponse> startCharge(
            HttpServletRequest request,
            @Valid @RequestBody StartChargeRequest chargeRequest) {
        Long userId = (Long) request.getAttribute("userId");
        StartChargeResponse response = chargeOrderService.startCharge(userId, chargeRequest);
        return Result.success(response);
    }

    @Operation(summary = "停止充电", description = "用户停止充电")
    @PostMapping("/stop")
    public Result<StopChargeResponse> stopCharge(
            HttpServletRequest request,
            @Valid @RequestBody StopChargeRequest stopRequest) {
        Long userId = (Long) request.getAttribute("userId");
        StopChargeResponse response = chargeOrderService.stopCharge(userId, stopRequest);
        return Result.success(response);
    }

    @Operation(summary = "查询充电状态", description = "根据订单号查询充电状态")
    @GetMapping("/status/{orderNo}")
    public Result<ChargeStatusResponse> getChargeStatus(
            HttpServletRequest request,
            @Parameter(description = "订单号") @PathVariable String orderNo) {
        Long userId = (Long) request.getAttribute("userId");
        ChargeStatusResponse response = chargeOrderService.getChargeStatus(userId, orderNo);
        return Result.success(response);
    }

    @Operation(summary = "获取当前充电订单", description = "获取用户当前正在进行的充电订单")
    @GetMapping("/current")
    public Result<ChargeStatusResponse> getCurrentChargeOrder(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        ChargeStatusResponse response = chargeOrderService.getCurrentChargeOrder(userId);
        return Result.success(response);
    }

    @Operation(summary = "查询充电订单列表", description = "分页查询用户的充电订单")
    @PostMapping("/orders")
    public Result<PageResponse<ChargeOrderListItem>> getUserChargeOrders(
            HttpServletRequest request,
            @RequestBody ChargeOrderQueryRequest queryRequest) {
        Long userId = (Long) request.getAttribute("userId");
        PageResponse<ChargeOrderListItem> response = chargeOrderService.getUserChargeOrders(userId, queryRequest);
        return Result.success(response);
    }

    @Operation(summary = "获取订单详情", description = "根据订单号获取充电订单详情")
    @GetMapping("/order/{orderNo}")
    public Result<ChargeOrderDetail> getOrderDetail(
            HttpServletRequest request,
            @Parameter(description = "订单号") @PathVariable String orderNo) {
        Long userId = (Long) request.getAttribute("userId");
        ChargeOrderDetail response = chargeOrderService.getOrderDetail(userId, orderNo);
        return Result.success(response);
    }
}
