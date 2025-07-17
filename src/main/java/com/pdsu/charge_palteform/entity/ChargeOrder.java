package com.pdsu.charge_palteform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
@TableName("charge_orders")
public class ChargeOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    private String connectorId;

    private String stationId;

    private String platformOrderNo;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private BigDecimal totalPower;

    private BigDecimal totalFee;

    private BigDecimal electricityFee;

    private BigDecimal serviceFee;

    /**
     * 订单状态：1-待充电，2-充电中，3-充电完成，4-已结算，5-已取消，6-异常
     */
    private Integer status;

    /**
     * 充电状态：1-启动中，2-充电中，3-停止中，4-已结束，5-未知
     */
    private Integer chargeStatus;

    /**
     * 停止原因：0-用户手动停止，1-平台停止，2-BMS停止，3-设备故障，4-连接器断开
     */
    private Integer stopReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Long couponId;
    private BigDecimal couponDeduction;
    private BigDecimal actualPayment;

    private BigDecimal soc;

    private Integer targetChargeDuration; // 目标充电时长（分钟）
    private BigDecimal targetSoc; // 目标电量百分比
    private BigDecimal targetAmount; // 目标充电金额
    private Integer stopCondition; // 停止条件：1-时间，2-电量，3-金额，4-手动
}
