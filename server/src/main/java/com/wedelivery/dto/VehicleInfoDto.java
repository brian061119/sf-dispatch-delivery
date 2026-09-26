package com.wedelivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 机器基础信息（需求 3）——接入到订单系统。
 * 「最大速度、续航」派生出的最大可配送路程同时接入到站点信息，用于判定是否可配送。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleInfoDto {

    private Long id;
    private String vehicleCode;
    private String vehicleType;
    private String vehicleTypeLabel;
    private Long stationId;

    /** 最大载重 kg */
    private BigDecimal maxWeight;
    /** 最大容积 m³ */
    private BigDecimal maxVolume;
    /** 默认最大速度 km/h */
    private BigDecimal cruiseSpeed;
    /** 满电续航时间 分钟 */
    private BigDecimal enduranceMinutes;
    /** 派生: 最大可配送路程 km = 续航时间 × 默认最大速度 */
    private BigDecimal maxDeliverableDistanceKm;
}
