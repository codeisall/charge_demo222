package com.pdsu.charge_palteform.enums;


import lombok.Getter;

@Getter
public enum ChargeOrderStatusEnum {
    CREATED(1, "已创建", "订单已创建，等待启动充电"),
    CHARGING(2, "充电中", "正在充电"),
    COMPLETED(3, "充电完成", "充电已完成，等待结算"),
    SETTLED(4, "已结算", "费用已结算"),
    CANCELLED(5, "已取消", "订单已取消"),
    FAILED(6, "异常", "充电异常或失败");

    private final Integer code;
    private final String desc;
    private final String detail;

    ChargeOrderStatusEnum(Integer code, String desc, String detail) {
        this.code = code;
        this.desc = desc;
        this.detail = detail;
    }

    public static ChargeOrderStatusEnum getByCode(Integer code) {
        for (ChargeOrderStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    public static String getDesc(Integer code) {
        ChargeOrderStatusEnum status = getByCode(code);
        return status != null ? status.getDesc() : "未知状态";
    }
}
