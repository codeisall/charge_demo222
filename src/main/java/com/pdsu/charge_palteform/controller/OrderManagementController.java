package com.pdsu.charge_palteform.controller;

import com.pdsu.charge_palteform.common.Result;
import com.pdsu.charge_palteform.service.DataConsistencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "订单状态管理", description = "订单状态管理和数据一致性检查")
@Slf4j
@RestController
@RequestMapping("/api/order/management")
@RequiredArgsConstructor
public class OrderManagementController {

    private final DataConsistencyService dataConsistencyService;

    @Operation(summary = "手动触发数据一致性检查", description = "手动检查所有订单的数据一致性")
    @PostMapping("/consistency/check")
    public Result<String> triggerConsistencyCheck() {
        try {
            dataConsistencyService.manualConsistencyCheck();
            return Result.success("数据一致性检查已触发");
        } catch (Exception e) {
            log.error("触发数据一致性检查失败", e);
            return Result.error("检查失败: " + e.getMessage());
        }
    }

    @Operation(summary = "检查单个订单一致性", description = "检查指定订单的数据一致性")
    @PostMapping("/consistency/check/{orderNo}")
    public Result<String> checkSingleOrderConsistency(@PathVariable String orderNo) {
        try {
            boolean result = dataConsistencyService.checkSingleOrderConsistency(orderNo);

            if (result) {
                return Result.success("订单状态已同步更新");
            } else {
                return Result.success("订单状态无变化或检查失败");
            }
        } catch (Exception e) {
            log.error("检查订单{}一致性失败", orderNo, e);
            return Result.error("检查失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取数据同步状态", description = "获取当前数据同步服务的运行状态")
    @GetMapping("/sync/status")
    public Result<Map<String, Object>> getSyncStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("syncEnabled", true);
            status.put("lastCheckTime", System.currentTimeMillis());
            status.put("status", "running");
            status.put("message", "数据同步服务运行正常");

            return Result.success(status);
        } catch (Exception e) {
            return Result.error("获取状态失败: " + e.getMessage());
        }
    }

    @Operation(summary = "订单状态统计", description = "获取各种状态的订单数量统计")
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getOrderStatistics() {
        try {
            Map<String, Object> statistics = new HashMap<>();
            // 这里可以添加统计逻辑
            statistics.put("totalOrders", 0);
            statistics.put("chargingOrders", 0);
            statistics.put("completedOrders", 0);
            statistics.put("failedOrders", 0);
            statistics.put("lastUpdateTime", System.currentTimeMillis());
            return Result.success(statistics);
        } catch (Exception e) {
            return Result.error("获取统计失败: " + e.getMessage());
        }
    }
}
