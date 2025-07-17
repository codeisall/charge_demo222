package com.pdsu.charge_palteform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pdsu.charge_palteform.entity.NotificationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NotificationRecordMapper extends BaseMapper<NotificationRecord> {

    /**
     * 查询失败的通知记录用于重试
     */
    @Select("SELECT * FROM notification_records WHERE status = 3 AND retry_count < 3 " +
            "AND create_time > #{since} ORDER BY create_time DESC LIMIT #{limit}")
    List<NotificationRecord> selectFailedRecords(@Param("since") LocalDateTime since,
                                                 @Param("limit") Integer limit);

    /**
     * 统计用户通知数量
     */
    @Select("SELECT COUNT(*) FROM notification_records WHERE user_id = #{userId} " +
            "AND create_time >= #{startTime} AND create_time <= #{endTime}")
    Long countUserNotifications(@Param("userId") Long userId,
                                @Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime);
}
