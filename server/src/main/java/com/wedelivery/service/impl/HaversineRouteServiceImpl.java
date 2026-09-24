package com.wedelivery.service.impl;

import com.wedelivery.entity.enums.VehicleType;
import com.wedelivery.service.RouteService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class HaversineRouteServiceImpl implements RouteService {

    private static final double EARTH_RADIUS_KM = 6371.0088;
    private static final double ROBOT_MANHATTAN_FACTOR = 1.35;

    @Override
    public double calculateStraightDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(rLat1) * Math.cos(rLat2) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    @Override
    public double calculateSegmentDistance(double lat1, double lon1, double lat2, double lon2, VehicleType vehicleType) {
        double straight = calculateStraightDistance(lat1, lon1, lat2, lon2);
        if (vehicleType == VehicleType.ROBOT) {
            return straight * ROBOT_MANHATTAN_FACTOR;
        }
        return straight;
    }

    @Override
    public BigDecimal calculateClosedLoopDistance(
            BigDecimal stationLat, BigDecimal stationLng,
            BigDecimal pickupLat, BigDecimal pickupLng,
            BigDecimal dropoffLat, BigDecimal dropoffLng,
            boolean isStationPickup,
            VehicleType vehicleType
    ) {
        double sLat = stationLat.doubleValue();
        double sLng = stationLng.doubleValue();
        double pLat = isStationPickup ? sLat : pickupLat.doubleValue();
        double pLng = isStationPickup ? sLng : pickupLng.doubleValue();
        double dLat = dropoffLat.doubleValue();
        double dLng = dropoffLng.doubleValue();

        double distStationToPickup = calculateSegmentDistance(sLat, sLng, pLat, pLng, vehicleType);
        double distPickupToDropoff = calculateSegmentDistance(pLat, pLng, dLat, dLng, vehicleType);
        double distDropoffToStation = calculateSegmentDistance(dLat, dLng, sLat, sLng, vehicleType);

        double total = distStationToPickup + distPickupToDropoff + distDropoffToStation;
        return BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public int estimateTravelTimeMinutes(double distanceKm, double cruiseSpeedKmH) {
        if (cruiseSpeedKmH <= 0) {
            cruiseSpeedKmH = 30.0;
        }
        double hours = distanceKm / cruiseSpeedKmH;
        return (int) Math.ceil(hours * 60.0);
    }
}
