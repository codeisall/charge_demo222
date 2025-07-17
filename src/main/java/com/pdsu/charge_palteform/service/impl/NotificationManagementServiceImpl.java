package com.pdsu.charge_palteform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pdsu.charge_palteform.entity.NotificationRecord;
import com.pdsu.charge_palteform.entity.dto.NotificationRecordResponse;
import com.pdsu.charge_palteform.entity.dto.PageResponse;
import com.pdsu.charge_palteform.mapper.NotificationRecordMapper;
import com.pdsu.charge_palteform.service.NotificationManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationManagementServiceImpl extends ServiceImpl<NotificationRecordMapper, NotificationRecord>
        implements NotificationManagementService {

    private final NotificationRecordMapper notificationRecordMapper;

    @Override
    public PageResponse<NotificationRecordResponse> getUserNotifications(Long userId, Integer pageNum, Integer pageSize) {
        Page<NotificationRecord> page = new Page<>(pageNum, pageSize);
        Page<NotificationRecord> result = page(page,
                new LambdaQueryWrapper<NotificationRecord>()
                        .eq(NotificationRecord::getUserId, userId)
                        .orderByDesc(NotificationRecord::getCreateTime)
        );

        List<NotificationRecordResponse> responseList = result.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        return PageResponse.of(responseList, result.getTotal(), pageNum, pageSize);
    }

    @Override
    @Scheduled(fixedRate = 300000) // 每5分钟执行一次
    public void retryFailedNotifications() {
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(24); // 只重试24小时内的失败通知
            List<NotificationRecord> failedRecords = notificationRecordMapper.selectFailedRecords(since, 50);

            if (failedRecords.isEmpty()) {
                return;
            }

            log.info("开始重试失败的通知，共{}条", failedRecords.size());

            for (NotificationRecord record : failedRecords) {
                try {
                    // 这里可以重新发送通知，暂时只是更新重试次数
                    record.setRetryCount(record.getRetryCount() + 1);
                    record.setUpdateTime(LocalDateTime.now());
                    updateById(record);

                } catch (Exception e) {
                    log.error("重试通知失败: recordId={}", record.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("重试失败通知任务异常", e);
        }
    }

    @Override
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredRecords() {
        try {
            LocalDateTime expireTime = LocalDateTime.now().minusDays(30); // 清理30天前的记录

            int deleted = notificationRecordMapper.delete(
                    new LambdaQueryWrapper<NotificationRecord>()
                            .lt(NotificationRecord::getCreateTime, expireTime)
            );
            if (deleted > 0) {
                log.info("清理过期通知记录{}条", deleted);
            }
        } catch (Exception e) {
            log.error("清理过期通知记录失败", e);
        }
    }

    private NotificationRecordResponse convertToResponse(NotificationRecord record) {
        NotificationRecordResponse response = new NotificationRecordResponse();
        response.setId(record.getId());
        response.setType(record.getType());
        response.setTypeText(getTypeText(record.getType()));
        response.setTitle(record.getTitle());
        response.setContent(record.getContent());
        response.setRelateOrderNo(record.getRelateOrderNo());
        response.setStatus(record.getStatus());
        response.setStatusText(getStatusText(record.getStatus()));
        response.setSendTime(record.getSendTime());
        response.setCreateTime(record.getCreateTime());
        return response;
    }

    private String getTypeText(String type) {
        switch (type) {
            case "charge_start": return "充电启动";
            case "charge_complete": return "充电完成";
            case "charge_fault": return "充电异常";
            case "charge_progress": return "充电进度";
            case "payment_success": return "支付成功";
            default: return "未知";
        }
    }

    private String getStatusText(Integer status) {
        switch (status) {
            case 1: return "待发送";
            case 2: return "发送成功";
            case 3: return "发送失败";
            default: return "未知";
        }
    }
}
