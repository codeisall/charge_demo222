/*
 Navicat Premium Data Transfer

 Source Server         : mysql
 Source Server Type    : MySQL
 Source Server Version : 80026
 Source Host           : localhost:3306
 Source Schema         : energy_db

 Target Server Type    : MySQL
 Target Server Version : 80026
 File Encoding         : 65001

 Date: 18/07/2025 08:51:24
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for balance_records
-- ----------------------------
DROP TABLE IF EXISTS `balance_records`;
CREATE TABLE `balance_records`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `user_id` bigint(0) NOT NULL COMMENT '用户ID',
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '关联订单号',
  `amount` decimal(10, 2) NOT NULL COMMENT '变动金额(元)，正数为增加，负数为减少',
  `balance_before` decimal(10, 2) NOT NULL COMMENT '变动前余额(元)',
  `balance_after` decimal(10, 2) NOT NULL COMMENT '变动后余额(元)',
  `type` tinyint(0) NOT NULL COMMENT '流水类型：1-充值，2-充电消费，3-退款，4-优惠券抵扣',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '描述',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_order_no`(`order_no`) USING BTREE,
  INDEX `idx_type`(`type`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户余额流水表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for charge_details
-- ----------------------------
DROP TABLE IF EXISTS `charge_details`;
CREATE TABLE `charge_details`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `charge_order_id` bigint(0) NOT NULL COMMENT '充电订单ID',
  `start_time` datetime(0) NOT NULL COMMENT '时段开始时间',
  `end_time` datetime(0) NULL DEFAULT NULL COMMENT '时段结束时间',
  `electricity_price` decimal(8, 4) NOT NULL COMMENT '时段电价(元/度)',
  `service_price` decimal(8, 4) NOT NULL COMMENT '时段服务费(元/度)',
  `power` decimal(10, 3) NULL DEFAULT 0.000 COMMENT '时段充电量(度)',
  `electricity_fee` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '时段电费(元)',
  `service_fee` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '时段服务费(元)',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_charge_order_id`(`charge_order_id`) USING BTREE,
  INDEX `idx_start_time`(`start_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '充电明细表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for charge_orders
-- ----------------------------
DROP TABLE IF EXISTS `charge_orders`;
CREATE TABLE `charge_orders`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单号',
  `user_id` bigint(0) NOT NULL COMMENT '用户ID',
  `connector_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '充电桩ID',
  `station_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '充电站ID',
  `platform_order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '平台订单号（对接电能平台的订单号）',
  `start_time` datetime(0) NULL DEFAULT NULL COMMENT '开始充电时间',
  `end_time` datetime(0) NULL DEFAULT NULL COMMENT '结束充电时间',
  `total_power` decimal(10, 3) NULL DEFAULT 0.000 COMMENT '累计充电量(度)',
  `total_fee` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '总费用(元)',
  `soc` decimal(5, 2) NULL DEFAULT NULL COMMENT '当前电池电量百分比',
  `target_charge_duration` int(0) NULL DEFAULT NULL COMMENT '目标充电时长（分钟）',
  `target_soc` decimal(5, 2) NULL DEFAULT NULL COMMENT '目标电量百分比',
  `target_amount` decimal(10, 2) NULL DEFAULT NULL COMMENT '目标充电金额',
  `stop_condition` tinyint(0) NULL DEFAULT NULL COMMENT '停止条件：1-时间，2-电量，3-金额，4-手动',
  `electricity_fee` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '电费(元)',
  `service_fee` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '服务费(元)',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '订单状态：1-待充电，2-充电中，3-充电完成，4-已结算，5-已取消，6-异常',
  `charge_status` tinyint(0) NULL DEFAULT NULL COMMENT '充电状态：1-启动中，2-充电中，3-停止中，4-已结束，5-未知',
  `stop_reason` tinyint(0) NULL DEFAULT NULL COMMENT '停止原因：0-用户手动停止，1-平台停止，2-BMS停止，3-设备故障，4-连接器断开',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `coupon_id` bigint(0) NULL DEFAULT NULL COMMENT '使用的优惠券ID',
  `coupon_deduction` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '优惠券抵扣金额',
  `actual_payment` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '实际支付金额',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_no`(`order_no`) USING BTREE,
  UNIQUE INDEX `uk_platform_order_no`(`platform_order_no`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_connector_id`(`connector_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE,
  INDEX `idx_coupon_id`(`coupon_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '充电订单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for charging_connectors
-- ----------------------------
DROP TABLE IF EXISTS `charging_connectors`;
CREATE TABLE `charging_connectors`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `connector_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '充电桩ID',
  `station_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属充电站ID',
  `connector_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '充电桩名称',
  `connector_type` tinyint(0) NOT NULL COMMENT '充电枪类型：1-直流，2-交流',
  `rated_power` decimal(8, 2) NULL DEFAULT NULL COMMENT '额定功率(kW)',
  `current_power` decimal(8, 2) NULL DEFAULT 0.00 COMMENT '当前功率(kW)',
  `electricity_fee` decimal(8, 4) NULL DEFAULT NULL COMMENT '电费单价(元/度)',
  `service_fee` decimal(8, 4) NULL DEFAULT NULL COMMENT '服务费单价(元/度)',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '充电桩状态：0-离网，1-空闲，2-占用（充电中），3-占用（未充电），4-预约，255-故障',
  `status_update_time` datetime(0) NULL DEFAULT NULL COMMENT '状态更新时间',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_connector_id`(`connector_id`) USING BTREE,
  INDEX `idx_station_id`(`station_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '充电桩表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for charging_stations
-- ----------------------------
DROP TABLE IF EXISTS `charging_stations`;
CREATE TABLE `charging_stations`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `station_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '充电站ID',
  `station_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '充电站名称',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '详细地址',
  `province` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '省份',
  `city` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '城市',
  `district` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '区县',
  `longitude` decimal(10, 7) NULL DEFAULT NULL COMMENT '经度',
  `latitude` decimal(10, 7) NULL DEFAULT NULL COMMENT '纬度',
  `station_tel` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系电话',
  `service_fee` decimal(8, 4) NULL DEFAULT NULL COMMENT '服务费',
  `parking_fee` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '停车费说明',
  `opening_hours` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '营业时间',
  `station_status` tinyint(0) NOT NULL DEFAULT 2 COMMENT '充电站状态：0-未知，1-建设中，2-运营中，3-关闭下线',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_station_id`(`station_id`) USING BTREE,
  INDEX `idx_location`(`province`, `city`, `district`) USING BTREE,
  INDEX `idx_status`(`station_status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '充电站表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for coupon_applicable_stations
-- ----------------------------
DROP TABLE IF EXISTS `coupon_applicable_stations`;
CREATE TABLE `coupon_applicable_stations`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `coupon_template_id` bigint(0) NOT NULL COMMENT '模板ID',
  `station_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '充电站ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_template_station`(`coupon_template_id`, `station_id`) USING BTREE,
  INDEX `station_id`(`station_id`) USING BTREE,
  CONSTRAINT `coupon_applicable_stations_ibfk_1` FOREIGN KEY (`coupon_template_id`) REFERENCES `coupon_templates` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `coupon_applicable_stations_ibfk_2` FOREIGN KEY (`station_id`) REFERENCES `charging_stations` (`station_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '优惠券适用站点表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for coupon_templates
-- ----------------------------
DROP TABLE IF EXISTS `coupon_templates`;
CREATE TABLE `coupon_templates`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '优惠券名称',
  `type` tinyint(0) NOT NULL COMMENT '类型：1-满减券，2-折扣券，3-现金券',
  `value` decimal(10, 2) NOT NULL COMMENT '优惠值(根据类型：金额/折扣率)',
  `min_charge_amount` decimal(10, 2) NULL DEFAULT NULL COMMENT '最低消费金额',
  `applicable_station_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '适用站点：1-全站通用，2-指定站点',
  `validity_type` tinyint(0) NOT NULL COMMENT '有效期类型：1-固定日期，2-领取后生效',
  `start_date` date NULL DEFAULT NULL COMMENT '有效期开始日期',
  `end_date` date NULL DEFAULT NULL COMMENT '有效期结束日期',
  `valid_days` smallint(0) NULL DEFAULT NULL COMMENT '有效天数(领取后N天内有效)',
  `total_quantity` int(0) NOT NULL COMMENT '发放总量',
  `remaining_quantity` int(0) NOT NULL COMMENT '剩余数量',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-停用',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_end_date`(`end_date`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '优惠券模板表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for payment_orders
-- ----------------------------
DROP TABLE IF EXISTS `payment_orders`;
CREATE TABLE `payment_orders`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '支付订单ID',
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '支付订单号',
  `user_id` bigint(0) NOT NULL COMMENT '用户ID',
  `relate_order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '关联订单号（充电订单号）',
  `wx_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '微信支付订单号',
  `wx_transaction_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '微信支付交易号',
  `amount` decimal(10, 2) NOT NULL COMMENT '支付金额(元)',
  `type` tinyint(0) NOT NULL COMMENT '支付类型：1-充值，2-充电消费',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '支付状态：1-待支付，2-支付成功，3-支付失败，4-已退款',
  `pay_time` datetime(0) NULL DEFAULT NULL COMMENT '支付时间',
  `refund_time` datetime(0) NULL DEFAULT NULL COMMENT '退款时间',
  `refund_amount` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '退款金额(元)',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `coupon_deduction` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '优惠券抵扣金额',
  `actual_amount` decimal(10, 2) NULL DEFAULT NULL COMMENT '实际支付金额',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_no`(`order_no`) USING BTREE,
  UNIQUE INDEX `uk_wx_order_no`(`wx_order_no`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_type_status`(`type`, `status`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '支付订单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_coupons
-- ----------------------------
DROP TABLE IF EXISTS `user_coupons`;
CREATE TABLE `user_coupons`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` bigint(0) NOT NULL COMMENT '用户ID',
  `coupon_template_id` bigint(0) NOT NULL COMMENT '模板ID',
  `coupon_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '优惠券唯一编码',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '状态：1-未使用，2-已使用，3-已过期',
  `receive_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
  `valid_start` datetime(0) NOT NULL COMMENT '有效期开始',
  `valid_end` datetime(0) NOT NULL COMMENT '有效期结束',
  `use_time` datetime(0) NULL DEFAULT NULL COMMENT '使用时间',
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '使用的订单号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_coupon_code`(`coupon_code`) USING BTREE,
  INDEX `idx_user_status`(`user_id`, `status`) USING BTREE,
  INDEX `idx_valid_end`(`valid_end`) USING BTREE,
  INDEX `coupon_template_id`(`coupon_template_id`) USING BTREE,
  CONSTRAINT `user_coupons_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `user_coupons_ibfk_2` FOREIGN KEY (`coupon_template_id`) REFERENCES `coupon_templates` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户优惠券表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `openid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '微信openid',
  `unionid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '微信unionid',
  `nickname` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户昵称',
  `avatar_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户头像',
  `phone` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '手机号',
  `balance` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '账户余额',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '用户状态：1-正常，0-禁用',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_openid`(`openid`) USING BTREE,
  INDEX `idx_phone`(`phone`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
