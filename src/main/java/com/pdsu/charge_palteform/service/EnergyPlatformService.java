package com.pdsu.charge_palteform.service;

import com.pdsu.charge_palteform.entity.platefrom.charge.ChargePolicyInfo;
import com.pdsu.charge_palteform.entity.platefrom.charge.ChargeStatusData;
import com.pdsu.charge_palteform.entity.platefrom.station.StationInfo;
import com.pdsu.charge_palteform.entity.platefrom.station.StationStatusInfo;

import java.util.List;

public interface EnergyPlatformService {


    /**
     * 获取电能平台访问Token
     */
    String getAccessToken();

    /**
     * 查询充电站信息
     */
    List<StationInfo> queryStationsInfo(String lastQueryTime, Integer pageNo, Integer pageSize);

    /**
     * 查询充电站状态
     */
    List<StationStatusInfo> queryStationStatus(List<String> stationIds);

    /**
     * 验证Token是否有效
     */
    boolean validateToken(String token);


    // ===================== 充电相关接口 =====================

    /**
     * 请求设备认证
     * @param connectorId 充电桩ID
     * @return 认证结果
     */
    boolean authenticateConnector(String connectorId);

    /**
     * 查询充电业务策略
     * @param connectorId 充电桩ID
     * @return 业务策略信息
     */
    ChargePolicyInfo getChargePolicy(String connectorId);

    /**
     * 启动充电
     * @param orderNo 本地订单号
     * @param connectorId 充电桩ID
     * @param qrCode 二维码信息（可选）
     * @return 平台订单号
     */
    String startCharge(String orderNo, String connectorId, String qrCode);

    /**
     * 停止充电
     * @param platformOrderNo 平台订单号
     * @param connectorId 充电桩ID
     * @return 停止结果
     */
    boolean stopCharge(String platformOrderNo, String connectorId);

    /**
     * 查询充电状态
     * @param platformOrderNo 平台订单号
     * @return 充电状态信息
     */
    ChargeStatusData queryChargeStatus(String platformOrderNo);

}
