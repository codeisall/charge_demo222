package com.pdsu.charge_palteform.service;

import com.pdsu.charge_palteform.entity.dto.NotificationRecordResponse;
import com.pdsu.charge_palteform.entity.dto.PageResponse;

public interface NotificationManagementService {
    /**
     * 获取用户通知记录
     */
    PageResponse<NotificationRecordResponse> getUserNotifications(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 重试失败的通知
     */
    void retryFailedNotifications();

    /**
     * 清理过期的通知记录
     */
    void cleanExpiredRecords();
}
