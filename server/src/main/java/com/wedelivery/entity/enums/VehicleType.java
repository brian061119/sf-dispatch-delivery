package com.wedelivery.entity.enums;

import java.math.BigDecimal;

/**
 * 载具类型及其出厂能力基线（对应《系统架构与数据库设计说明书》2.1 节能力约束模型）。
 * 这里只给出「类型级默认值」：机器基础信息导入时若未显式给出能力字段，则按此兜底。
 * 派单准入一律以载具自身的基础信息（vehicles 表）为准，类型默认值仅作兜底与展示。
 */
public enum VehicleType {
    DRONE("无人机",
            new BigDecimal("3.00"),
            new BigDecimal("0.05"),
            new BigDecimal("45.00"),
            new BigDecimal("30.00"),
            new BigDecimal("4.0")),
    ROBOT("机器人",
            new BigDecimal("15.00"),
            new BigDecimal("0.30"),
            new BigDecimal("15.00"),
            new BigDecimal("240.00"),
            new BigDecimal("2.0"));

    private final String label;
    /** 最大载重 kg */
    private final BigDecimal defaultMaxWeight;
    /** 最大容积 m³ */
    private final BigDecimal defaultMaxVolume;
    /** 默认最大速度 km/h */
    private final BigDecimal defaultCruiseSpeed;
    /** 满电续航时间 分钟 */
    private final BigDecimal defaultEnduranceMinutes;
    /** 单位能耗率 % 电量 / km */
    private final BigDecimal energyRatePercentPerKm;

    VehicleType(String label,
                BigDecimal defaultMaxWeight,
                BigDecimal defaultMaxVolume,
                BigDecimal defaultCruiseSpeed,
                BigDecimal defaultEnduranceMinutes,
                BigDecimal energyRatePercentPerKm) {
        this.label = label;
        this.defaultMaxWeight = defaultMaxWeight;
        this.defaultMaxVolume = defaultMaxVolume;
        this.defaultCruiseSpeed = defaultCruiseSpeed;
        this.defaultEnduranceMinutes = defaultEnduranceMinutes;
        this.energyRatePercentPerKm = energyRatePercentPerKm;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getDefaultMaxWeight() {
        return defaultMaxWeight;
    }

    public BigDecimal getDefaultMaxVolume() {
        return defaultMaxVolume;
    }

    public BigDecimal getDefaultCruiseSpeed() {
        return defaultCruiseSpeed;
    }

    public BigDecimal getDefaultEnduranceMinutes() {
        return defaultEnduranceMinutes;
    }

    public BigDecimal getEnergyRatePercentPerKm() {
        return energyRatePercentPerKm;
    }
}
