package com.pdsu.charge_palteform.controller;

import com.pdsu.charge_palteform.common.Result;
import com.pdsu.charge_palteform.entity.dto.NotificationRecordResponse;
import com.pdsu.charge_palteform.entity.dto.PageResponse;
import com.pdsu.charge_palteform.service.NotificationManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
@Tag(name = "通知管理", description = "用户通知相关接口")
public class NotificationController {

    private final NotificationManagementService notificationManagementService;


    @Operation(summary = "获取用户通知记录", description = "分页获取用户的通知记录")
    @GetMapping("/list")
    public Result<PageResponse<NotificationRecordResponse>> getUserNotifications(
            HttpServletRequest request,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "页面大小") @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = (Long) request.getAttribute("userId");
        PageResponse<NotificationRecordResponse> response = notificationManagementService.getUserNotifications(userId, pageNum, pageSize);
        return Result.success(response);
    }

}
