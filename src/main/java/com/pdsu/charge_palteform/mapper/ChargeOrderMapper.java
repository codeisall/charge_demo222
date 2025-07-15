package com.pdsu.charge_palteform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pdsu.charge_palteform.entity.ChargeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ChargeOrderMapper extends BaseMapper<ChargeOrder> {
    /**
     * 统计用户订单数量按状态分组
     */
    @Select("SELECT status, COUNT(*) as count FROM charge_orders " +
            "WHERE user_id = #{userId} GROUP BY status")
    List<Map<String, Object>> countOrdersByStatus(@Param("userId") Long userId);

    /**
     * 查询用户最近的充电订单
     */
    @Select("SELECT * FROM charge_orders WHERE user_id = #{userId} " +
            "ORDER BY create_time DESC LIMIT #{limit}")
    List<ChargeOrder> getRecentOrders(@Param("userId") Long userId, @Param("limit") Integer limit);

    /**
     * 查询指定时间范围内的订单
     */
    @Select("SELECT * FROM charge_orders WHERE user_id = #{userId} " +
            "AND create_time >= #{startTime} AND create_time <= #{endTime} " +
            "ORDER BY create_time DESC")
    List<ChargeOrder> getOrdersByTimeRange(@Param("userId") Long userId,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);

    /**
     * 查询用户在指定充电站的订单
     */
    @Select("SELECT * FROM charge_orders WHERE user_id = #{userId} AND station_id = #{stationId} " +
            "ORDER BY create_time DESC")
    List<ChargeOrder> getOrdersByStation(@Param("userId") Long userId, @Param("stationId") String stationId);
}
