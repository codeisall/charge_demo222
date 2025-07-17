package com.pdsu.charge_palteform.enums;


import lombok.Getter;

@Getter
public enum PlatformChargeStatusEnum {
    STARTING(1, "启动中", "正在启动充电"),
    CHARGING(2, "充电中", "正在充电"),
    STOPPING(3, "停止中", "正在停止充电"),
    FINISHED(4, "已结束", "充电已结束"),
    UNKNOWN(5, "未知", "状态未知");

    private final Integer code;
    private final String desc;
    private final String detail;

    PlatformChargeStatusEnum(Integer code, String desc, String detail) {
        this.code = code;
        this.desc = desc;
        this.detail = detail;
    }

    public static PlatformChargeStatusEnum getByCode(Integer code) {
        for (PlatformChargeStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    public static String getDesc(Integer code) {
        PlatformChargeStatusEnum status = getByCode(code);
        return status != null ? status.getDesc() : "未知状态";
    }

    /**
     * 将平台状态映射为订单状态
     */
    public ChargeOrderStatusEnum mapToOrderStatus() {
        switch (this) {
            case STARTING:
            case CHARGING:
            case STOPPING:
                return ChargeOrderStatusEnum.CHARGING;
            case FINISHED:
                return ChargeOrderStatusEnum.COMPLETED;
            default:
                return null; // 不改变订单状态
        }
    }
}
