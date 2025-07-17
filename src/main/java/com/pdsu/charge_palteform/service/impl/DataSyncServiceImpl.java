package com.pdsu.charge_palteform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pdsu.charge_palteform.entity.ChargingConnector;
import com.pdsu.charge_palteform.entity.ChargingStation;
import com.pdsu.charge_palteform.entity.platefrom.station.ConnectorInfo;
import com.pdsu.charge_palteform.entity.platefrom.station.ConnectorStatusInfo;
import com.pdsu.charge_palteform.entity.platefrom.station.EquipmentInfo;
import com.pdsu.charge_palteform.entity.platefrom.station.StationInfo;
import com.pdsu.charge_palteform.mapper.ChargingConnectorMapper;
import com.pdsu.charge_palteform.mapper.ChargingStationMapper;
import com.pdsu.charge_palteform.service.DataSyncService;
import com.pdsu.charge_palteform.service.EnergyPlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSyncServiceImpl implements DataSyncService {
    private final EnergyPlatformService energyPlatformService;
    private final ChargingStationMapper stationMapper;
    private final ChargingConnectorMapper connectorMapper;

    @Override
    @Transactional
    public void syncStationInfo() {
        log.info("开始同步充电站基础信息...");
        try {
            int pageNo = 1;
            int pageSize = 100;
            boolean hasMore = true;
            int totalSynced = 0;

            while (hasMore) {
                // 从电能平台获取充电站信息
                List<StationInfo> stationInfos = energyPlatformService.queryStationsInfo(
                        null, pageNo, pageSize);

                if (CollectionUtils.isEmpty(stationInfos)) {
                    if (pageNo == 1) {
                        log.warn("第一页没有获取到充电站数据，可能是电能平台配置问题或网络问题");
                    }
                    hasMore = false;
                    break;
                }

                // 处理每个充电站
                for (StationInfo stationInfo : stationInfos) {
                    try {
                        syncSingleStation(stationInfo);
                        totalSynced++;
                    } catch (Exception e) {
                        log.error("同步充电站{}失败: {}", stationInfo.getStationID(), e.getMessage());
                        // 继续处理下一个充电站，不因为单个充电站失败而终止整个同步过程
                    }
                }

                // 如果返回的数据少于pageSize，说明已经是最后一页
                if (stationInfos.size() < pageSize) {
                    hasMore = false;
                }
                pageNo++;
                log.info("已同步第{}页，本页{}个充电站，累计{}个", pageNo - 1, stationInfos.size(), totalSynced);

                // 防止无限循环，最多同步100页
                if (pageNo > 100) {
                    log.warn("已同步100页，停止同步以防止无限循环");
                    hasMore = false;
                }
            }

            if (totalSynced > 0) {
                log.info("✅ 充电站基础信息同步完成，共同步{}个充电站", totalSynced);
            } else {
                log.warn("⚠️  没有同步到任何充电站数据，请检查电能平台配置或网络连接");
            }

        } catch (Exception e) {
            log.error("❌ 同步充电站基础信息失败", e);
            throw new RuntimeException("同步充电站信息失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void syncConnectorStatus() {
        log.info("开始同步充电桩状态信息...");
        try {
            // 获取所有充电站ID
            List<ChargingStation> stations = stationMapper.selectList(null);
            List<String> stationIds = stations.stream()
                    .map(ChargingStation::getStationId)
                    .collect(Collectors.toList());

            if (CollectionUtils.isEmpty(stationIds)) {
                log.warn("没有找到充电站，请先同步充电站基础信息");
                return;
            }

            log.info("找到{}个充电站，开始同步状态信息", stationIds.size());
            log.info("充电站ID列表: {}", stationIds);

            // 分批查询状态（每次最多50个）
            int batchSize = 50;
            int totalBatches = (int) Math.ceil((double) stationIds.size() / batchSize);
            int successfulBatches = 0;

            for (int i = 0; i < stationIds.size(); i += batchSize) {
                int currentBatch = (i / batchSize) + 1;
                try {
                    int endIndex = Math.min(i + batchSize, stationIds.size());
                    List<String> batchIds = stationIds.subList(i, endIndex);

                    log.debug("正在同步第{}/{}批充电站状态，包含{}个充电站", currentBatch, totalBatches, batchIds.size());
                    log.debug("本批充电站ID: {}", batchIds); // 添加这行日志
                    // 查询这批充电站的状态
                    var statusInfos = energyPlatformService.queryStationStatus(batchIds);
                    log.info("电能平台返回状态信息: statusInfos={}", statusInfos);

                    if (!CollectionUtils.isEmpty(statusInfos)) {
                        // 更新充电桩状态
                        for (var statusInfo : statusInfos) {
                            if (statusInfo.getStationStatusInfos() != null) {
                                updateConnectorStatus(statusInfo.getStationStatusInfos());
                            }
                        }
                        successfulBatches++;
                    }
                    log.debug("第{}/{}批充电桩状态同步完成", currentBatch, totalBatches);
                } catch (Exception e) {
                    log.error("第{}/{}批充电桩状态同步失败: {}", currentBatch, totalBatches, e.getMessage());
                    // 继续处理下一批，不因为单批失败而终止整个同步过程
                }
            }

            log.info("✅ 充电桩状态同步完成，成功同步{}/{}批", successfulBatches, totalBatches);

        } catch (Exception e) {
            log.error("❌ 同步充电桩状态失败", e);
            throw new RuntimeException("同步充电桩状态失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void fullSync() {
        log.info("开始全量数据同步...");
        try {
            // 先同步基础信息，再同步状态信息
            syncStationInfo();
            // 等待一段时间，确保基础信息同步完成
            Thread.sleep(2000);
            syncConnectorStatus();
            log.info("✅ 全量数据同步完成");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("全量数据同步被中断", e);
        } catch (Exception e) {
            log.error("❌ 全量数据同步失败", e);
            throw new RuntimeException("全量数据同步失败: " + e.getMessage(), e);
        }
    }

    /**
     * 同步单个充电站信息
     */
    private void syncSingleStation(StationInfo stationInfo) {
        try {
            log.info("🔄 开始同步充电站: ID={}, 名称={}", stationInfo.getStationID(), stationInfo.getStationName());

            // 1. 同步充电站基础信息
            ChargingStation station = convertToChargingStation(stationInfo);

            // 查询是否已存在
            ChargingStation existingStation = stationMapper.selectOne(
                    new LambdaQueryWrapper<ChargingStation>()
                            .eq(ChargingStation::getStationId, station.getStationId())
            );

            if (existingStation != null) {
                // 更新现有充电站
                station.setId(existingStation.getId());
                station.setCreateTime(existingStation.getCreateTime());
                stationMapper.updateById(station);
                log.info("📝 更新充电站: ID={}, 名称={}", station.getStationId(), station.getStationName());
            } else {
                // 新增充电站
                stationMapper.insert(station);
                log.info("➕ 新增充电站: ID={}, 名称={}", station.getStationId(), station.getStationName());
            }

            // 2. 同步充电桩信息
            int totalSyncedConnectors = 0;
            if (!CollectionUtils.isEmpty(stationInfo.getEquipmentInfos())) {
                log.info("🔌 开始同步{}个设备的充电桩信息", stationInfo.getEquipmentInfos().size());

                for (int i = 0; i < stationInfo.getEquipmentInfos().size(); i++) {
                    EquipmentInfo equipmentInfo = stationInfo.getEquipmentInfos().get(i);
                    log.info("📱 同步设备{}/{}: ID={}, 类型={}",
                            i + 1, stationInfo.getEquipmentInfos().size(),
                            equipmentInfo.getEquipmentID(), equipmentInfo.getEquipmentType());

                    syncEquipmentConnectors(stationInfo.getStationID(), equipmentInfo);
                    log.info("✅ 设备{}同步完成", equipmentInfo.getEquipmentID());
                }
            } else {
                log.warn("⚠️  充电站{}没有设备信息", stationInfo.getStationID());
            }

            log.info("🎉 充电站{}同步完成，共同步{}个充电桩", stationInfo.getStationID(), totalSyncedConnectors);

        } catch (Exception e) {
            log.error("❌ 同步充电站{}失败: {}", stationInfo.getStationID(), e.getMessage());
            throw e; // 重新抛出异常，让上层处理
        }
    }

    /**
     * 同步设备下的充电桩
     */
    private void syncEquipmentConnectors(String stationId, EquipmentInfo equipmentInfo) {
        if (CollectionUtils.isEmpty(equipmentInfo.getConnectorInfos())) {
            log.warn("⚠️  设备{}没有充电桩信息", equipmentInfo.getEquipmentID());
            return ;
        }

        log.info("⚡ 开始同步设备{}的{}个充电桩",
                equipmentInfo.getEquipmentID(), equipmentInfo.getConnectorInfos().size());

        int syncedCount = 0;
        for (int i = 0; i < equipmentInfo.getConnectorInfos().size(); i++) {
            ConnectorInfo connectorInfo = equipmentInfo.getConnectorInfos().get(i);
            try {
                log.debug("🔌 同步充电桩{}/{}: ID={}, 类型={}, 功率={}kW",
                        i + 1, equipmentInfo.getConnectorInfos().size(),
                        connectorInfo.getConnectorID(), connectorInfo.getConnectorType(), connectorInfo.getPower());

                ChargingConnector connector = convertToChargingConnector(stationId, equipmentInfo, connectorInfo);

                // 查询是否已存在
                ChargingConnector existingConnector = connectorMapper.selectOne(
                        new LambdaQueryWrapper<ChargingConnector>()
                                .eq(ChargingConnector::getConnectorId, connector.getConnectorId())
                );

                if (existingConnector != null) {
                    // 更新现有充电桩（保留状态信息）
                    connector.setId(existingConnector.getId());
                    connector.setCreateTime(existingConnector.getCreateTime());
                    connector.setStatus(existingConnector.getStatus()); // 保留原有状态
                    connector.setStatusUpdateTime(existingConnector.getStatusUpdateTime());
                    connectorMapper.updateById(connector);
                    log.debug("📝 更新充电桩: {}", connector.getConnectorId());
                } else {
                    // 新增充电桩
                    connectorMapper.insert(connector);
                    log.debug("➕ 新增充电桩: {}", connector.getConnectorId());
                }

                syncedCount++;
            } catch (Exception e) {
                log.error("❌ 同步充电桩{}失败: {}", connectorInfo.getConnectorID(), e.getMessage());
                // 继续处理下一个充电桩
            }
        }

        log.info("✅ 设备{}充电桩同步完成: {}/{}",
                equipmentInfo.getEquipmentID(), syncedCount, equipmentInfo.getConnectorInfos().size());

        return ;
    }

    /**
     * 更新充电桩状态
     */
    private void updateConnectorStatus(List<ConnectorStatusInfo> statusInfos) {
        if (CollectionUtils.isEmpty(statusInfos)) {
            return;
        }

        int updatedCount = 0;
        for (ConnectorStatusInfo statusInfo : statusInfos) {
            try {
                int updated = connectorMapper.update(null,
                        new LambdaUpdateWrapper<ChargingConnector>()
                                .eq(ChargingConnector::getConnectorId, statusInfo.getConnectorID())
                                .set(ChargingConnector::getStatus, statusInfo.getStatus())
                                .set(ChargingConnector::getStatusUpdateTime, LocalDateTime.now())
                                .set(ChargingConnector::getUpdateTime, LocalDateTime.now()) // 手动设置
                );

                if (updated > 0) {
                    updatedCount++;
                    log.debug("更新充电桩{}状态为: {}", statusInfo.getConnectorID(), statusInfo.getStatus());
                } else {
                    log.warn("充电桩{}不存在，无法更新状态", statusInfo.getConnectorID());
                }
            } catch (Exception e) {
                log.error("更新充电桩{}状态失败: {}", statusInfo.getConnectorID(), e.getMessage());
            }
        }

        log.debug("成功更新{}个充电桩状态", updatedCount);
    }

    /**
     * 转换为充电站实体
     */
    private ChargingStation convertToChargingStation(StationInfo stationInfo) {
        ChargingStation station = new ChargingStation();
        station.setStationId(stationInfo.getStationID());
        station.setStationName(stationInfo.getStationName());
        station.setAddress(stationInfo.getAddress());

        // 解析区域编码 - 更安全的处理方式
        if (stationInfo.getAreaCode() != null && stationInfo.getAreaCode().length() >= 6) {
            String areaCode = stationInfo.getAreaCode();
            try {
                // 简化处理，实际应该有完整的区域码映射
                station.setProvince(areaCode.substring(0, 2) + "0000");
                station.setCity(areaCode.substring(0, 4) + "00");
                station.setDistrict(areaCode);
            } catch (Exception e) {
                log.warn("解析区域编码{}失败: {}", areaCode, e.getMessage());
            }
        }

        station.setLongitude(stationInfo.getStationLng());
        station.setLatitude(stationInfo.getStationLat());
        station.setStationTel(stationInfo.getStationTel());
        station.setParkingFee(stationInfo.getParkFee());
        station.setOpeningHours(stationInfo.getBusineHours());
        station.setStationStatus(stationInfo.getStationStatus());

        // 设置服务费 - 从ServiceFee字符串中解析
        if (stationInfo.getServiceFee() != null && !stationInfo.getServiceFee().trim().isEmpty()) {
            try {
                // 这里需要根据实际的ServiceFee格式进行解析
                // 暂时设置为固定值，实际应该解析时间段费率
                station.setServiceFee(BigDecimal.valueOf(0.5));
            } catch (Exception e) {
                log.warn("解析服务费{}失败: {}", stationInfo.getServiceFee(), e.getMessage());
                station.setServiceFee(BigDecimal.ZERO);
            }
        } else {
            station.setServiceFee(BigDecimal.ZERO);
        }

        return station;
    }

    /**
     * 转换为充电桩实体
     */
    private ChargingConnector convertToChargingConnector(String stationId, EquipmentInfo equipmentInfo, ConnectorInfo connectorInfo) {
        ChargingConnector connector = new ChargingConnector();
        connector.setConnectorId(connectorInfo.getConnectorID());
        connector.setStationId(stationId);
        connector.setConnectorName(connectorInfo.getConnectorName());

        // 转换充电桩类型：根据电能平台的ConnectorType映射到我们的系统
        // 1：家用插座，2：交流接口插座，3：交流接口插头，4：直流接口枪头，5：无线充电座，6：其他
        if (connectorInfo.getConnectorType() != null) {
            if (connectorInfo.getConnectorType() == 4) {
                connector.setConnectorType(1); // 直流
            } else if (connectorInfo.getConnectorType() == 2 || connectorInfo.getConnectorType() == 3) {
                connector.setConnectorType(2); // 交流
            } else {
                connector.setConnectorType(2); // 默认交流
            }
        } else {
            connector.setConnectorType(2); // 默认交流
        }

        connector.setRatedPower(connectorInfo.getPower());
        connector.setCurrentPower(BigDecimal.ZERO);
        connector.setStatus(1); // 默认空闲状态

        // 设置电费和服务费（简化处理，实际应该从业务策略接口获取）
        connector.setElectricityFee(BigDecimal.valueOf(1.0)); // 默认电费
        connector.setServiceFee(BigDecimal.valueOf(0.5)); // 默认服务费

        return connector;
    }
}
