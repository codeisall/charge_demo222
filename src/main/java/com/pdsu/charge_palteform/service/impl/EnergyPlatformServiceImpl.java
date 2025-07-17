package com.pdsu.charge_palteform.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdsu.charge_palteform.config.EnergyPlatformConfig;
import com.pdsu.charge_palteform.entity.platefrom.PlatformRequest;
import com.pdsu.charge_palteform.entity.platefrom.PlatformResponse;
import com.pdsu.charge_palteform.entity.platefrom.charge.*;
import com.pdsu.charge_palteform.entity.platefrom.station.*;
import com.pdsu.charge_palteform.entity.platefrom.token.TokenRequest;
import com.pdsu.charge_palteform.entity.platefrom.token.TokenResponse;
import com.pdsu.charge_palteform.exception.BusinessException;
import com.pdsu.charge_palteform.service.EnergyPlatformService;
import com.pdsu.charge_palteform.utils.AesUtil;
import com.pdsu.charge_palteform.utils.HMacMD5;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnergyPlatformServiceImpl implements EnergyPlatformService {


    private final EnergyPlatformConfig config;
    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicInteger seqCounter = new AtomicInteger(1);

    private static final String TOKEN_CACHE_KEY = "energy:platform:token";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    public String getAccessToken() {
        // 先检查配置是否完整
        if (!isConfigValid()) {
            log.warn("电能平台配置不完整，使用模拟模式");
            return "mock_token_for_development";
        }

        // 从缓存获取
        String cachedToken = redisTemplate.opsForValue().get(TOKEN_CACHE_KEY);
        if (cachedToken != null) {
            log.debug("使用缓存的Token: {}...", cachedToken.substring(0, Math.min(10, cachedToken.length())));
            return cachedToken;
        }
        // 缓存没有，请求新token
        return requestNewToken();
    }

    /**
     * 检查配置是否完整
     */
    private boolean isConfigValid() {
        return config.getBaseUrl() != null && !config.getBaseUrl().trim().isEmpty() &&
                config.getOperatorId() != null && !config.getOperatorId().trim().isEmpty() &&
                config.getOperatorSecret() != null && !config.getOperatorSecret().trim().isEmpty() &&
                config.getDataSecret() != null && !config.getDataSecret().trim().isEmpty() &&
                config.getDataSecretIv() != null && !config.getDataSecretIv().trim().isEmpty() &&
                config.getSigSecret() != null && !config.getSigSecret().trim().isEmpty();
    }

    /**
     * 请求新的Token
     */
    private String requestNewToken() {
        try {
            log.info("正在向电能平台请求新的访问Token...");

            // 1. 构建请求数据
            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setOperatorID(config.getOperatorId());
            tokenRequest.setOperatorSecret(config.getOperatorSecret());

            log.debug("Token请求参数: OperatorID={}, OperatorSecret={}***",
                    config.getOperatorId(),
                    config.getOperatorSecret().substring(0, Math.min(4, config.getOperatorSecret().length())));

            // 2. 加密数据
            String dataJson = objectMapper.writeValueAsString(tokenRequest);
            log.debug("加密前的数据: {}", dataJson);

            String encryptedData = AesUtil.encrypt(dataJson, config.getDataSecret(), config.getDataSecretIv());
            log.debug("加密后的数据: {}", encryptedData);

            // 3. 构建平台请求
            PlatformRequest request = buildPlatformRequest(encryptedData);

            // 4. 打印完整的请求信息
            log.info("发送请求到电能平台:");
            log.info("  URL: {}", config.getBaseUrl() + "/query_token");
            log.info("  OperatorID: {}", request.getOperatorID());
            log.info("  TimeStamp: {}", request.getTimeStamp());
            log.info("  Seq: {}", request.getSeq());
            log.info("  Sig: {}", request.getSig());
            log.info("  Data: {}...", request.getData().substring(0, Math.min(50, request.getData().length())));

            // 5. 发送请求
            String url = config.getBaseUrl() + "/query_token";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("User-Agent", "ChargingPlatform/1.0");

            HttpEntity<PlatformRequest> entity = new HttpEntity<>(request, headers);

            // 使用String接收原始响应，便于调试
            ResponseEntity<String> rawResponse = restTemplate.postForEntity(url, entity, String.class);

            log.info("收到原始响应:");
            log.info("  Status: {}", rawResponse.getStatusCode());
            log.info("  Headers: {}", rawResponse.getHeaders());
            log.info("  Body: {}", rawResponse.getBody());

            if (rawResponse.getBody() == null || rawResponse.getBody().trim().isEmpty()) {
                throw new BusinessException("电能平台返回空响应");
            }

            // 尝试解析JSON响应
            PlatformResponse response;
            try {
                response = objectMapper.readValue(rawResponse.getBody(), PlatformResponse.class);
                log.debug("解析后的响应: {}", response);
            } catch (Exception e) {
                log.error("JSON解析失败，原始响应: {}", rawResponse.getBody());
                throw new BusinessException("响应格式错误: " + e.getMessage());
            }

            if (response.getRet() == null) {
                throw new BusinessException("响应中缺少Ret字段，可能是接口地址错误或服务异常");
            }

            if (response.getRet() != 0) {
                throw new BusinessException("电能平台返回错误: Ret=" + response.getRet() + ", Msg=" + response.getMsg());
            }

            if (response.getData() == null || response.getData().trim().isEmpty()) {
                throw new BusinessException("响应数据为空");
            }

            // 6. 解密响应数据
            String decryptedData;
            try {
                decryptedData = AesUtil.decrypt(response.getData(), config.getDataSecret(), config.getDataSecretIv());
                log.debug("解密后的数据: {}", decryptedData);
            } catch (Exception e) {
                log.error("数据解密失败: {}", e.getMessage());
                throw new BusinessException("响应数据解密失败: " + e.getMessage());
            }

            TokenResponse tokenResponse = objectMapper.readValue(decryptedData, TokenResponse.class);
            log.debug("Token响应: {}", tokenResponse);

            if (tokenResponse.getSuccStat() != 0) {
                String errorMsg = getTokenErrorMessage(tokenResponse.getFailReason());
                throw new BusinessException("Token获取失败: " + errorMsg);
            }

            // 7. 缓存Token
            String token = tokenResponse.getAccessToken();
            if (token == null || token.trim().isEmpty()) {
                throw new BusinessException("返回的Token为空");
            }

            long cacheTime = Math.max(tokenResponse.getTokenAvailableTime() - 300, 60);
            redisTemplate.opsForValue().set(TOKEN_CACHE_KEY, token, cacheTime, TimeUnit.SECONDS);

            log.info("✅ 电能平台Token获取成功，有效期: {}秒", tokenResponse.getTokenAvailableTime());
            return token;

        } catch (Exception e) {
            log.error("❌ 获取电能平台Token失败", e);
            throw new BusinessException("获取电能平台Token失败: " + e.getMessage());
        }
    }

    /**
     * 获取Token错误信息
     */
    private String getTokenErrorMessage(Integer failReason) {
        if (failReason == null) {
            return "未知错误";
        }

        switch (failReason) {
            case 0:
                return "无错误";
            case 1:
                return "无此运营商";
            case 2:
                return "密钥错误";
            default:
                return "自定义错误码: " + failReason;
        }
    }

    @Override
    public List<StationInfo> queryStationsInfo(String lastQueryTime, Integer pageNo, Integer pageSize) {
        // 检查配置是否完整
        if (!isConfigValid()) {
            throw new BusinessException("电能平台配置不完整，请联系管理员配置相关参数");
        }
        try {
            log.info("========== 开始查询电能平台充电站信息 ==========");
            log.info("查询参数: lastQueryTime={}, pageNo={}, pageSize={}", lastQueryTime, pageNo, pageSize);
            // 1. 构建查询请求
            StationQueryPlatformRequest queryRequest = new StationQueryPlatformRequest();
            queryRequest.setLastQueryTime(lastQueryTime);
            queryRequest.setPageNo(pageNo);
            queryRequest.setPageSize(pageSize);
            // 2. 加密数据
            String dataJson = objectMapper.writeValueAsString(queryRequest);
            log.info("🔍 充电站查询请求原始数据: {}", dataJson);

            String encryptedData = AesUtil.encrypt(dataJson, config.getDataSecret(), config.getDataSecretIv());
            log.debug("🔐 加密后的请求数据: {}", encryptedData);

            // 3. 构建平台请求
            PlatformRequest request = buildPlatformRequest(encryptedData);
            log.info("📤 完整请求参数: OperatorID={}, TimeStamp={}, Seq={}",
                    request.getOperatorID(), request.getTimeStamp(), request.getSeq());

            // 4. 发送请求
            String url = config.getBaseUrl() + "/query_stations_info";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(getAccessToken());

            log.info("🌐 发送请求到电能平台: {}", url);
            log.debug("🔑 使用Token: {}...", getAccessToken().substring(0, Math.min(20, getAccessToken().length())));

            HttpEntity<PlatformRequest> entity = new HttpEntity<>(request, headers);

            // 使用String类型接收原始响应，便于调试
            ResponseEntity<String> rawResponse = restTemplate.postForEntity(url, entity, String.class);

            log.info("📥 电能平台原始响应状态: {}", rawResponse.getStatusCode());
            log.info("📥 电能平台原始响应内容: {}", rawResponse.getBody());

            if (rawResponse.getBody() == null || rawResponse.getBody().trim().isEmpty()) {
                throw new BusinessException("电能平台返回空响应");
            }

            // 解析响应
            PlatformResponse response;
            try {
                response = objectMapper.readValue(rawResponse.getBody(), PlatformResponse.class);
                log.info("✅ 解析后的平台响应: Ret={}, Msg={}, Data长度={}",
                        response.getRet(), response.getMsg(),
                        response.getData() != null ? response.getData().length() : 0);
            } catch (Exception e) {
                log.error("❌ JSON解析失败，原始响应: {}", rawResponse.getBody());
                throw new BusinessException("响应格式错误: " + e.getMessage());
            }

            if (response.getRet() != 0) {
                log.error("❌ 电能平台返回错误: Ret={}, Msg={}", response.getRet(), response.getMsg());
                throw new BusinessException("查询充电站信息失败: Ret=" + response.getRet() + ", Msg=" + response.getMsg());
            }

            if (response.getData() == null || response.getData().trim().isEmpty()) {
                log.warn("⚠️  电能平台返回的Data字段为空");
                return new ArrayList<>();
            }

            // 5. 解密响应数据
            String decryptedData;
            try {
                decryptedData = AesUtil.decrypt(response.getData(), config.getDataSecret(), config.getDataSecretIv());
                log.info("🔓 解密后的完整响应数据: {}", decryptedData);
            } catch (Exception e) {
                log.error("❌ 数据解密失败: {}", e.getMessage());
                throw new BusinessException("响应数据解密失败: " + e.getMessage());
            }

            // 6. 解析充电站列表
            StationQueryResponse queryResponse;
            try {
                queryResponse = objectMapper.readValue(decryptedData, StationQueryResponse.class);
                log.info("📊 解析后的查询响应: Total={}, StationInfos数量={}",
                        queryResponse.getTotal(),
                        queryResponse.getStationInfos() != null ? queryResponse.getStationInfos().size() : 0);
            } catch (Exception e) {
                log.error("❌ 充电站数据解析失败: {}", e.getMessage());
                log.error("原始解密数据: {}", decryptedData);
                throw new BusinessException("充电站数据解析失败: " + e.getMessage());
            }

            List<StationInfo> stationInfos = queryResponse.getStationInfos();
            if (stationInfos == null) {
                log.warn("⚠️  充电站列表为null，返回空列表");
                return new ArrayList<>();
            }

            log.info("🏪 成功查询到{}个充电站", stationInfos.size());

            // 详细打印每个充电站的信息
            for (int i = 0; i < stationInfos.size(); i++) {
                StationInfo station = stationInfos.get(i);
                log.info("📍 充电站{}: ID={}, 名称={}, 地址={}",
                        i + 1, station.getStationID(), station.getStationName(), station.getAddress());

                // 打印设备信息
                if (station.getEquipmentInfos() != null && !station.getEquipmentInfos().isEmpty()) {
                    log.info("   🔌 包含{}个设备", station.getEquipmentInfos().size());

                    int totalConnectors = 0;
                    for (int j = 0; j < station.getEquipmentInfos().size(); j++) {
                        EquipmentInfo equipment = station.getEquipmentInfos().get(j);
                        int connectorCount = equipment.getConnectorInfos() != null ? equipment.getConnectorInfos().size() : 0;
                        totalConnectors += connectorCount;

                        log.info("   📱 设备{}: ID={}, 类型={}, 充电桩数={}",
                                j + 1, equipment.getEquipmentID(), equipment.getEquipmentType(), connectorCount);

                        // 打印充电桩信息
                        if (equipment.getConnectorInfos() != null) {
                            for (int k = 0; k < equipment.getConnectorInfos().size(); k++) {
                                ConnectorInfo connector = equipment.getConnectorInfos().get(k);
                                log.info("     ⚡ 充电桩{}: ID={}, 类型={}, 功率={}kW",
                                        k + 1, connector.getConnectorID(), connector.getConnectorType(), connector.getPower());
                            }
                        }
                    }

                    log.info("   📊 充电站{}总计{}个充电桩", station.getStationID(), totalConnectors);
                } else {
                    log.warn("   ⚠️  充电站{}没有配置设备信息", station.getStationID());
                }
            }

            // 统计信息
            int totalConnectors = stationInfos.stream()
                    .mapToInt(station -> {
                        if (station.getEquipmentInfos() != null) {
                            return station.getEquipmentInfos().stream()
                                    .mapToInt(equipment ->
                                            equipment.getConnectorInfos() != null ? equipment.getConnectorInfos().size() : 0)
                                    .sum();
                        }
                        return 0;
                    })
                    .sum();

            log.info("📈 查询汇总: 充电站数量={}, 总充电桩数量={}", stationInfos.size(), totalConnectors);
            log.info("========== 充电站信息查询完成 ==========");

            return stationInfos;

        } catch (Exception e) {
            log.error("❌ 查询电能平台充电站信息失败", e);
            throw new BusinessException("查询充电站信息失败: " + e.getMessage());
        }
    }


    //throw new BusinessException("电能平台配置不完整，请联系管理员配置相关参数");
    @Override
    public List<StationStatusInfo> queryStationStatus(List<String> stationIds) {
        // 检查配置是否完整
        if (!isConfigValid()) {
            throw new BusinessException("电能平台配置不完整，请联系管理员配置相关参数");
        }
        try {
            log.info("查询{}个充电站状态: {}", stationIds.size(), stationIds);
            // 1. 构建状态查询请求
            StationStatusRequest statusRequest = new StationStatusRequest();
            statusRequest.setStationIDs(stationIds);
            // 2. 加密数据
            String dataJson = objectMapper.writeValueAsString(statusRequest);
            log.info("状态查询请求原始数据: {}", dataJson);
            String encryptedData = AesUtil.encrypt(dataJson, config.getDataSecret(), config.getDataSecretIv());
            log.debug("状态查询加密后数据: {}", encryptedData);
            // 3. 构建平台请求
            PlatformRequest request = buildPlatformRequest(encryptedData);
            log.info("完整请求参数: OperatorID={}, TimeStamp={}, Seq={}",
                    request.getOperatorID(), request.getTimeStamp(), request.getSeq());
            // 4. 发送请求
            String url = config.getBaseUrl() + "/query_station_status";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(getAccessToken());
            log.info("发送状态查询请求到: {}", url);
            HttpEntity<PlatformRequest> entity = new HttpEntity<>(request, headers);
            ResponseEntity<String> rawResponse = restTemplate.postForEntity(url, entity, String.class);
            log.info("电能平台原始响应状态: {}", rawResponse.getStatusCode());
            log.info("电能平台原始响应内容: {}", rawResponse.getBody());
            if (rawResponse.getBody() == null || rawResponse.getBody().trim().isEmpty()) {
                throw new BusinessException("电能平台返回空响应");
            }
            // 解析响应
            PlatformResponse response;
            try {
                response = objectMapper.readValue(rawResponse.getBody(), PlatformResponse.class);
                log.info("解析后的平台响应: Ret={}, Msg={}, Data长度={}",
                        response.getRet(), response.getMsg(),
                        response.getData() != null ? response.getData().length() : 0);
            } catch (Exception e) {
                log.error("JSON解析失败，原始响应: {}", rawResponse.getBody());
                throw new BusinessException("响应格式错误: " + e.getMessage());
            }
            if (response.getRet() != 0) {
                log.error("电能平台返回错误: Ret={}, Msg={}", response.getRet(), response.getMsg());
                throw new BusinessException("查询充电站状态失败: Ret=" + response.getRet() + ", Msg=" + response.getMsg());
            }
            if (response.getData() == null || response.getData().trim().isEmpty()) {
                log.warn("电能平台返回的Data字段为空");
                return new ArrayList<>();
            }
            // 5. 解密响应数据
            String decryptedData;
            try {
                decryptedData = AesUtil.decrypt(response.getData(), config.getDataSecret(), config.getDataSecretIv());
                log.info("状态查询解密后的完整数据: {}", decryptedData);
            } catch (Exception e) {
                log.error("数据解密失败: {}", e.getMessage());
                throw new BusinessException("响应数据解密失败: " + e.getMessage());
            }
            // 6. 解析状态响应
            StationStatusResponse statusResponse;
            try {
                statusResponse = objectMapper.readValue(decryptedData, StationStatusResponse.class);
                log.info("解析后的状态响应对象: {}", statusResponse);
            } catch (Exception e) {
                log.error("状态响应解析失败: {}", e.getMessage());
                log.error("原始解密数据: {}", decryptedData);
                throw new BusinessException("状态响应解析失败: " + e.getMessage());
            }

            if (statusResponse.getStationStatusInfos() == null) {
                log.warn("状态响应中没有StationStatusInfos字段，完整响应: {}", statusResponse);
                return new ArrayList<>();
            }
            log.info("成功查询到{}个充电站的状态信息", statusResponse.getStationStatusInfos().size());
            // 打印详细的状态信息用于调试
            for (int i = 0; i < statusResponse.getStationStatusInfos().size(); i++) {
                StationStatusInfo info = statusResponse.getStationStatusInfos().get(i);
                log.info("充电站{}状态信息: 包含{}个充电桩状态", i,
                        info.getStationStatusInfos() != null ? info.getStationStatusInfos().size() : 0);

                if (info.getStationStatusInfos() != null) {
                    for (ConnectorStatusInfo connectorInfo : info.getStationStatusInfos()) {
                        log.info("  充电桩ID: {}, 状态: {}", connectorInfo.getConnectorID(), connectorInfo.getStatus());
                    }
                }
            }
            return statusResponse.getStationStatusInfos();

        } catch (Exception e) {
            log.error("查询充电站状态失败", e);
            throw new BusinessException("查询充电站状态失败: " + e.getMessage());
        }
    }



    @Override
    public boolean validateToken(String token) {
        try {
            // 这里可以调用电能平台的Token验证接口，暂时简化处理
            return token != null && !token.isEmpty();
        } catch (Exception e) {
            log.error("验证Token失败", e);
            return false;
        }
    }

    @Override
    public boolean authenticateConnector(String connectorId) {
        if (!isConfigValid()) {
            throw new BusinessException("电能平台配置不完整，请联系管理员配置相关参数");
        }

        try {
            log.info("请求设备认证: {}", connectorId);

            // 构建设备认证请求
            ConnectorAuthRequest authRequest = new ConnectorAuthRequest();
            authRequest.setEquipAuthSeq(generateAuthSeq());
            authRequest.setConnectorID(connectorId);

            // 加密数据
            String dataJson = objectMapper.writeValueAsString(authRequest);
            log.debug("设备认证请求JSON: {}", dataJson);
            String encryptedData = AesUtil.encrypt(dataJson, config.getDataSecret(), config.getDataSecretIv());

            // 构建平台请求
            PlatformRequest request = buildPlatformRequest(encryptedData);

            // 发送请求
            String url = config.getBaseUrl() + "/query_equip_auth";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(getAccessToken());

            log.info("发送设备认证请求到: {}", url);
            HttpEntity<PlatformRequest> entity = new HttpEntity<>(request, headers);
            ResponseEntity<PlatformResponse> response = restTemplate.postForEntity(url, entity, PlatformResponse.class);

            log.info("设备认证响应: {}", response.getBody());

            if (response.getBody() == null || response.getBody().getRet() != 0) {
                log.error("设备认证失败，平台返回错误");
                return false;
            }

            // 解密响应数据
            String decryptedData = AesUtil.decrypt(response.getBody().getData(),
                    config.getDataSecret(), config.getDataSecretIv());
            log.debug("设备认证解密后数据: {}", decryptedData);

            ConnectorAuthResponse authResponse = objectMapper.readValue(decryptedData, ConnectorAuthResponse.class);

            boolean success = authResponse.getSuccStat() == 0;
            if (!success) {
                log.warn("设备认证失败，原因代码: {}", authResponse.getFailReason());
            }

            log.info("设备认证结果: {}", success ? "成功" : "失败");
            return success;

        } catch (Exception e) {
            log.error("设备认证异常", e);
            throw new BusinessException("设备认证失败: " + e.getMessage());
        }
    }

    @Override
    public ChargePolicyInfo getChargePolicy(String connectorId) {
        if (!isConfigValid()) {
            throw new BusinessException("电能平台配置不完整，请联系管理员配置相关参数");
        }

        try {
            log.info("查询充电业务策略: {}", connectorId);

            // 构建策略查询请求
            ChargePolicyRequest policyRequest = new ChargePolicyRequest();
            policyRequest.setEquipBizSeq(generateBizSeq());
            policyRequest.setConnectorID(connectorId);

            // 加密数据
            String dataJson = objectMapper.writeValueAsString(policyRequest);
            log.debug("策略查询请求JSON: {}", dataJson);
            String encryptedData = AesUtil.encrypt(dataJson, config.getDataSecret(), config.getDataSecretIv());

            // 构建平台请求
            PlatformRequest request = buildPlatformRequest(encryptedData);

            // 发送请求
            String url = config.getBaseUrl() + "/query_equip_business_policy";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(getAccessToken());

            log.info("发送策略查询请求到: {}", url);
            HttpEntity<PlatformRequest> entity = new HttpEntity<>(request, headers);
            ResponseEntity<PlatformResponse> response = restTemplate.postForEntity(url, entity, PlatformResponse.class);

            log.info("策略查询响应: {}", response.getBody());

            if (response.getBody() == null || response.getBody().getRet() != 0) {
                throw new BusinessException("查询充电策略失败");
            }

            // 解密响应数据
            String decryptedData = AesUtil.decrypt(response.getBody().getData(),
                    config.getDataSecret(), config.getDataSecretIv());
            log.debug("策略查询解密后数据: {}", decryptedData);

            ChargePolicyResponse policyResponse = objectMapper.readValue(decryptedData, ChargePolicyResponse.class);

            if (policyResponse.getSuccStat() != 0) {
                throw new BusinessException("查询充电策略失败，错误代码: " + policyResponse.getFailReason());
            }

            return convertToChargePolicyInfo(policyResponse);

        } catch (Exception e) {
            log.error("查询充电策略失败", e);
            throw new BusinessException("查询充电策略失败: " + e.getMessage());
        }
    }

    @Override
    public String startCharge(String orderNo, String connectorId, String qrCode) {
        if (!isConfigValid()) {
            throw new BusinessException("电能平台配置不完整，请联系管理员配置相关参数");
        }

        try {
            log.info("请求启动充电: orderNo={}, connectorId={}", orderNo, connectorId);

            // 构建启动充电请求
            PlatformStartChargeRequest startRequest = new PlatformStartChargeRequest();
            startRequest.setStartChargeSeq(generateChargeSeq(orderNo));
            startRequest.setConnectorID(connectorId);
            if (qrCode != null && !qrCode.trim().isEmpty()) {
                startRequest.setQRCode(qrCode);
            }

            // 加密数据
            String dataJson = objectMapper.writeValueAsString(startRequest);
            log.debug("启动充电请求JSON: {}", dataJson);
            String encryptedData = AesUtil.encrypt(dataJson, config.getDataSecret(), config.getDataSecretIv());

            // 构建平台请求
            PlatformRequest request = buildPlatformRequest(encryptedData);

            // 发送请求
            String url = config.getBaseUrl() + "/query_start_charge";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(getAccessToken());

            log.info("发送启动充电请求到: {}", url);
            HttpEntity<PlatformRequest> entity = new HttpEntity<>(request, headers);
            ResponseEntity<PlatformResponse> response = restTemplate.postForEntity(url, entity, PlatformResponse.class);

            log.info("启动充电响应: {}", response.getBody());

            if (response.getBody() == null || response.getBody().getRet() != 0) {
                throw new BusinessException("启动充电请求失败");
            }

            // 解密响应数据
            String decryptedData = AesUtil.decrypt(response.getBody().getData(),
                    config.getDataSecret(), config.getDataSecretIv());
            log.debug("启动充电解密后数据: {}", decryptedData);

            PlatformStartChargeResponse startResponse = objectMapper.readValue(decryptedData, PlatformStartChargeResponse.class);

            if (startResponse.getSuccStat() != 0) {
                String errorMsg = getStartChargeErrorMessage(startResponse.getFailReason());
                throw new BusinessException("启动充电失败: " + errorMsg);
            }

            log.info("启动充电成功，平台订单号: {}", startResponse.getStartChargeSeq());
            return startResponse.getStartChargeSeq();

        } catch (Exception e) {
            log.error("启动充电异常", e);
            throw new BusinessException("启动充电失败: " + e.getMessage());
        }
    }

    @Override
    public boolean stopCharge(String platformOrderNo, String connectorId) {
        if (!isConfigValid()) {
            throw new BusinessException("电能平台配置不完整，请联系管理员配置相关参数");
        }

        try {
            log.info("请求停止充电: platformOrderNo={}, connectorId={}", platformOrderNo, connectorId);

            // 构建停止充电请求
            PlatformStopChargeRequest stopRequest = new PlatformStopChargeRequest();
            stopRequest.setStartChargeSeq(platformOrderNo);
            stopRequest.setConnectorID(connectorId);

            // 加密数据
            String dataJson = objectMapper.writeValueAsString(stopRequest);
            log.debug("停止充电请求JSON: {}", dataJson);
            String encryptedData = AesUtil.encrypt(dataJson, config.getDataSecret(), config.getDataSecretIv());

            // 构建平台请求
            PlatformRequest request = buildPlatformRequest(encryptedData);

            // 发送请求
            String url = config.getBaseUrl() + "/query_stop_charge";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(getAccessToken());

            log.info("发送停止充电请求到: {}", url);
            HttpEntity<PlatformRequest> entity = new HttpEntity<>(request, headers);
            ResponseEntity<PlatformResponse> response = restTemplate.postForEntity(url, entity, PlatformResponse.class);

            log.info("停止充电响应: {}", response.getBody());

            if (response.getBody() == null || response.getBody().getRet() != 0) {
                log.error("停止充电请求失败");
                return false;
            }

            // 解密响应数据
            String decryptedData = AesUtil.decrypt(response.getBody().getData(),
                    config.getDataSecret(), config.getDataSecretIv());
            log.debug("停止充电解密后数据: {}", decryptedData);

            PlatformStopChargeResponse stopResponse = objectMapper.readValue(decryptedData, PlatformStopChargeResponse.class);

            boolean success = stopResponse.getSuccStat() == 0;
            if (!success) {
                log.warn("停止充电失败，原因代码: {}", stopResponse.getFailReason());
            }

            log.info("停止充电结果: {}", success ? "成功" : "失败");
            return success;

        } catch (Exception e) {
            log.error("停止充电异常", e);
            throw new BusinessException("停止充电失败: " + e.getMessage());
        }
    }

    @Override
    public ChargeStatusData queryChargeStatus(String platformOrderNo) {
        if (!isConfigValid()) {
            throw new BusinessException("电能平台配置不完整，请联系管理员配置相关参数");
        }

        try {
            log.info("查询充电状态: {}", platformOrderNo);

            // 构建状态查询请求
            ChargeStatusRequest statusRequest = new ChargeStatusRequest();
            statusRequest.setStartChargeSeq(platformOrderNo);

            // 加密数据
            String dataJson = objectMapper.writeValueAsString(statusRequest);
            log.debug("状态查询请求JSON: {}", dataJson);
            String encryptedData = AesUtil.encrypt(dataJson, config.getDataSecret(), config.getDataSecretIv());

            // 构建平台请求
            PlatformRequest request = buildPlatformRequest(encryptedData);

            // 发送请求
            String url = config.getBaseUrl() + "/query_equip_charge_status";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(getAccessToken());

            log.info("发送状态查询请求到: {}", url);
            HttpEntity<PlatformRequest> entity = new HttpEntity<>(request, headers);
            ResponseEntity<PlatformResponse> response = restTemplate.postForEntity(url, entity, PlatformResponse.class);

            log.info("状态查询响应: {}", response.getBody());

            if (response.getBody() == null || response.getBody().getRet() != 0) {
                throw new BusinessException("查询充电状态失败");
            }

            // 解密响应数据
            String decryptedData = AesUtil.decrypt(response.getBody().getData(),
                    config.getDataSecret(), config.getDataSecretIv());
            log.debug("状态查询解密后数据: {}", decryptedData);

            PlatformChargeStatusResponse statusResponse = objectMapper.readValue(decryptedData, PlatformChargeStatusResponse.class);

            return convertToChargeStatusData(statusResponse);

        } catch (Exception e) {
            log.error("查询充电状态失败", e);
            throw new BusinessException("查询充电状态失败: " + e.getMessage());
        }
    }

    /**
     * 构建电能平台通用请求
     */
    private PlatformRequest buildPlatformRequest(String encryptedData) {
        PlatformRequest request = new PlatformRequest();
        request.setOperatorID(config.getOperatorId());
        request.setData(encryptedData);

        // 生成时间戳
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        request.setTimeStamp(timestamp);

        // 生成序列号
        String seq = String.format("%04d", seqCounter.getAndIncrement() % 10000);
        request.setSeq(seq);

        // 生成签名
        String signContent = config.getOperatorId() + encryptedData + timestamp + seq;
        String signature = HMacMD5.getHmacMd5Str(config.getSigSecret(), signContent);
        request.setSig(signature);

        log.debug("构建请求签名:");
        log.debug("  签名内容: {}", signContent);
        log.debug("  签名密钥: {}***", config.getSigSecret().substring(0, Math.min(4, config.getSigSecret().length())));
        log.debug("  最终签名: {}", signature);

        return request;
    }




    // 辅助方法
    private String generateAuthSeq() {
        return config.getOperatorId() + System.currentTimeMillis();
    }

    private String generateBizSeq() {
        return config.getOperatorId() + System.currentTimeMillis();
    }

    private String generateChargeSeq(String orderNo) {
        return config.getOperatorId() + orderNo.substring(2) + String.format("%04d", seqCounter.getAndIncrement() % 10000);
    }

    private String getStartChargeErrorMessage(Integer failReason) {
        if (failReason == null) return "未知错误";
        switch (failReason) {
            case 0: return "无错误";
            case 1: return "此设备不存在";
            case 2: return "此设备离线";
            case 3: return "电桩枪口充电中";
            default: return "错误代码: " + failReason;
        }
    }

    private ChargePolicyInfo convertToChargePolicyInfo(ChargePolicyResponse response) {
        ChargePolicyInfo info = new ChargePolicyInfo();
        info.setConnectorId(response.getConnectorID());
        info.setSumPeriod(response.getSumPeriod());

        if (response.getPolicyInfos() != null && !response.getPolicyInfos().isEmpty()) {
            List<ChargePolicyInfo.PolicyPeriod> periods = response.getPolicyInfos().stream()
                    .map(policy -> {
                        ChargePolicyInfo.PolicyPeriod period = new ChargePolicyInfo.PolicyPeriod();
                        period.setStartTime(policy.getStartTime());
                        period.setElectricityPrice(new BigDecimal(policy.getElecPrice()));
                        period.setServicePrice(new BigDecimal(policy.getSevicePrice()));
                        return period;
                    })
                    .collect(Collectors.toList());
            info.setPeriods(periods);

            // 设置当前费率（这里简化为第一个时段的费率）
            if (!periods.isEmpty()) {
                info.setCurrentElectricityPrice(periods.get(0).getElectricityPrice());
                info.setCurrentServicePrice(periods.get(0).getServicePrice());
            }
        }

        return info;
    }

    private ChargeStatusData convertToChargeStatusData(PlatformChargeStatusResponse response) {
        ChargeStatusData data = new ChargeStatusData();
        data.setPlatformOrderNo(response.getStartChargeSeq());
        data.setConnectorId(response.getConnectorID());
        data.setChargeStatus(response.getStartChargeSeqStat());
        data.setConnectorStatus(response.getConnectorStatus());

        // 电气参数
        data.setCurrentA(response.getCurrentA());
        data.setCurrentB(response.getCurrentB());
        data.setCurrentC(response.getCurrentC());
        data.setVoltageA(response.getVoltageA());
        data.setVoltageB(response.getVoltageB());
        data.setVoltageC(response.getVoltageC());
        data.setSoc(response.getSoc());

        // 时间信息
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            if (response.getStartTime() != null && !response.getStartTime().trim().isEmpty()) {
                data.setStartTime(LocalDateTime.parse(response.getStartTime(), formatter));
            }
            if (response.getEndTime() != null && !response.getEndTime().trim().isEmpty()) {
                data.setEndTime(LocalDateTime.parse(response.getEndTime(), formatter));
            }
        } catch (Exception e) {
            log.warn("解析时间格式失败: {}", e.getMessage());
        }

        // 费用信息
        data.setTotalPower(response.getTotalPower());
        data.setElectricityFee(response.getElecMoney());
        data.setServiceFee(response.getSeviceMoney());
        data.setTotalFee(response.getTotalMoney());

        // 充电明细
        if (response.getChargeDetails() != null && !response.getChargeDetails().isEmpty()) {
            List<ChargeStatusData.ChargeDetailData> details = response.getChargeDetails().stream()
                    .map(detail -> {
                        ChargeStatusData.ChargeDetailData detailData = new ChargeStatusData.ChargeDetailData();
                        try {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                            if (detail.getDetailStartTime() != null) {
                                detailData.setStartTime(LocalDateTime.parse(detail.getDetailStartTime(), formatter));
                            }
                            if (detail.getDetailEndTime() != null) {
                                detailData.setEndTime(LocalDateTime.parse(detail.getDetailEndTime(), formatter));
                            }
                        } catch (Exception e) {
                            log.warn("解析明细时间格式失败: {}", e.getMessage());
                        }
                        detailData.setElectricityPrice(detail.getElecPrice());
                        detailData.setServicePrice(detail.getSevicePrice());
                        detailData.setPower(detail.getDetailPower());
                        detailData.setElectricityFee(detail.getDetailElecMoney());
                        detailData.setServiceFee(detail.getDetailSeviceMoney());
                        return detailData;
                    })
                    .collect(Collectors.toList());
            data.setChargeDetails(details);
        }

        return data;
    }




}
