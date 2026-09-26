package com.wedelivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 机器实时信息（需求 4）——接入到实时追踪系统。
 * 位置来源为地图接入，状态/速度为机器接入。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleRealtimeDto {

    private String vehicleCode;
    private String vehicleType;
    private String vehicleTypeLabel;

    /** 待命 / 配送中 / 充电 / 故障 / 关机 */
    private String status;
    private String statusLabel;

    /** 位置编码: 0=不在任何站点, 1/2/3=位于对应站点 id */
    private Integer locationCode;
    /** 位置的文字描述（如「站点 1 - SF Downtown Hub」或「不在任何站点（配送途中）」） */
    private String locationLabel;

    private BigDecimal currentLat;
    private BigDecimal currentLng;
    /** 当前速度 km/h */
    private BigDecimal currentSpeed;
    private BigDecimal batteryLevel;

    private LocalDateTime positionUpdatedAt;
    private LocalDateTime statusUpdatedAt;
    private LocalDateTime speedUpdatedAt;
    private LocalDateTime updatedAt;
}
