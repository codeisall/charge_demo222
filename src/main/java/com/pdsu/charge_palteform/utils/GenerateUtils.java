package com.pdsu.charge_palteform.utils;

import java.util.UUID;

public class GenerateUtils {


    //随机生成券码
    public static String generateCouponCode() {
        return "CPN" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    //生成订单号
    public static String generateOrderNo() {
        return "CO" + System.currentTimeMillis() + String.format("%04d", (int)(Math.random() * 10000));
    }

}
