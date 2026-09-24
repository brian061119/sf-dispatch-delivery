package com.wedelivery.service;

import com.wedelivery.entity.enums.VehicleType;

import java.math.BigDecimal;

public interface RouteService {

    /**
     * 计算两点间直线距离 (Haversine 公式，单位 km)
     */
    double calculateStraightDistance(double lat1, double lon1, double lat2, double lon2);

    /**
     * 计算单段航程距离（无人机直线，地面机器人 1.35x 曼哈顿系数）
     */
    double calculateSegmentDistance(double lat1, double lon1, double lat2, double lon2, VehicleType vehicleType);

    /**
     * 计算全闭环总里程 (Door-to-Door 或 Station-Pickup)
     */
    BigDecimal calculateClosedLoopDistance(
            BigDecimal stationLat, BigDecimal stationLng,
            BigDecimal pickupLat, BigDecimal pickupLng,
            BigDecimal dropoffLat, BigDecimal dropoffLng,
            boolean isStationPickup,
            VehicleType vehicleType
    );

    /**
     * 根据里程和巡航速度推算耗时 (分钟)
     */
    int estimateTravelTimeMinutes(double distanceKm, double cruiseSpeedKmH);
}
