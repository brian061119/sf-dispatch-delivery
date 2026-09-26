package com.wedelivery.entity.enums;

/**
 * 载具实时状态（机器接入上报 / 调度模块流转）。
 * 入库取值即枚举名，故不可随意改名，改名前需同步存量数据。
 */
public enum VehicleStatus {
    IDLE("待命"),
    IN_DELIVERY("配送中"),
    CHARGING("充电"),
    FAULT("故障"),
    OFFLINE("关机");

    private final String label;

    VehicleStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
