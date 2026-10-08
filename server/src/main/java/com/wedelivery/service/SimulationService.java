package com.wedelivery.service;

import com.wedelivery.dto.SimulationTickResponse;
import com.wedelivery.dto.VehicleRealtimeDto;
import com.wedelivery.entity.Order;
import com.wedelivery.entity.Station;
import com.wedelivery.entity.Vehicle;
import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.entity.enums.VehicleStatus;
import com.wedelivery.repository.OrderRepository;
import com.wedelivery.repository.StationRepository;
import com.wedelivery.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Demo Simulator: Replaces hardware telemetry and map polling heartbeats to advance realtime vehicle states.
 *
 * Position progression leverages {@link TrackingService#trackOrder} interpolation so that vehicle coordinates
 * and client tracking views stay completely synchronized.
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class SimulationService {

    private final OrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;
    private final StationRepository stationRepository;
    private final TrackingService trackingService;
    private final VehicleService vehicleService;
    private final RouteService routeService;

    /** Simulated driving duration per tick (minutes) */
    private static final double TICK_MINUTES = 1.0;
    /** Recharge rate: percentage of battery recharged per tick */
    private static final BigDecimal CHARGE_RATE_PER_TICK = new BigDecimal("10.00");
    /** Distance threshold (km) to consider vehicle arrived at station, aligned with VehicleService parking radius */
    private static final double ARRIVE_RADIUS_KM = 0.15;

    private static final List<OrderStatus> ACTIVE_ORDER_STATUSES =
            Arrays.asList(OrderStatus.PAID, OrderStatus.PICKING_UP, OrderStatus.IN_TRANSIT);

    @Transactional
    public SimulationTickResponse tick() {
        LocalDateTime now = LocalDateTime.now();
        Set<Long> movingVehicleIds = new HashSet<>();

        // 1. Advance all active orders: interpolate positions and update vehicle realtime coords
        List<Order> activeOrders = orderRepository.findByStatusIn(ACTIVE_ORDER_STATUSES);
        for (Order order : activeOrders) {
            trackingService.trackOrder(order.getOrderNumber());
            if (order.getVehicleId() != null) {
                movingVehicleIds.add(order.getVehicleId());
            }
        }

        Map<Long, Station> stations = new HashMap<>();
        for (Station s : stationRepository.findAll()) {
            stations.put(s.getId(), s);
        }

        int returned = 0;
        int charged = 0;

        for (Vehicle v : vehicleRepository.findAll()) {
            if (v.getStatus() == VehicleStatus.IN_DELIVERY) {
                // 2. Vehicles still marked IN_DELIVERY without active order: return to target/home station step by step
                if (v.getId() != null && !movingVehicleIds.contains(v.getId())) {
                    Station targetStation = (v.getTargetStationId() != null && stations.containsKey(v.getTargetStationId()))
                            ? stations.get(v.getTargetStationId())
                            : stations.get(v.getStationId());
                    if (returnToStation(v, targetStation, now)) {
                        returned++;
                    }
                }
            } else if (v.getStatus() == VehicleStatus.CHARGING) {
                // 3. Recharging: switch to IDLE once battery reaches 100%
                BigDecimal chargedLevel = v.getBatteryLevel().add(CHARGE_RATE_PER_TICK);
                if (chargedLevel.compareTo(Vehicle.FULL_BATTERY) >= 0) {
                    v.setBatteryLevel(Vehicle.FULL_BATTERY);
                    v.setStatus(VehicleStatus.IDLE);
                    v.setStatusUpdatedAt(now);
                    v.setCurrentSpeed(BigDecimal.ZERO);
                    v.setSpeedUpdatedAt(now);
                    charged++;
                } else {
                    v.setBatteryLevel(chargedLevel.setScale(2, RoundingMode.HALF_UP));
                }
                vehicleRepository.save(v);
            }
        }

        log.info("Simulation tick: activeOrders={}, movingVehicles={}, returned={}, charged={}",
                activeOrders.size(), movingVehicleIds.size(), returned, charged);

        return SimulationTickResponse.builder()
                .tickAt(now)
                .movedVehicles(movingVehicleIds.size())
                .returnedVehicles(returned)
                .chargedVehicles(charged)
                .vehicles(vehicleService.listVehicleRealtime())
                .build();
    }

    /**
     * Advances an unassigned vehicle towards its assigned station by one tick.
     * @return true if vehicle has docked at station and returned to IDLE
     */
    private boolean returnToStation(Vehicle v, Station station, LocalDateTime now) {
        if (station == null) {
            return false;
        }

        double sLat = station.getLatitude().doubleValue();
        double sLng = station.getLongitude().doubleValue();

        // Unknown position: park directly to avoid blank coords
        if (v.getCurrentLat() == null || v.getCurrentLng() == null) {
            parkAtStation(v, station, now);
            return true;
        }

        double vLat = v.getCurrentLat().doubleValue();
        double vLng = v.getCurrentLng().doubleValue();
        double remainingKm = routeService.calculateStraightDistance(vLat, vLng, sLat, sLng);

        if (remainingKm <= ARRIVE_RADIUS_KM) {
            parkAtStation(v, station, now);
            return true;
        }

        double stepKm = v.getCruiseSpeed().doubleValue() / 60.0 * TICK_MINUTES;
        if (stepKm >= remainingKm) {
            parkAtStation(v, station, now);
            return true;
        }

        double ratio = stepKm / remainingKm;
        double nextLat = vLat + ratio * (sLat - vLat);
        double nextLng = vLng + ratio * (sLng - vLng);

        v.setCurrentLat(BigDecimal.valueOf(nextLat).setScale(7, RoundingMode.HALF_UP));
        v.setCurrentLng(BigDecimal.valueOf(nextLng).setScale(7, RoundingMode.HALF_UP));
        v.setLocationCode(Vehicle.LOCATION_NOT_AT_STATION);
        v.setCurrentSpeed(v.getCruiseSpeed());
        v.setPositionUpdatedAt(now);
        v.setSpeedUpdatedAt(now);

        // Empty return battery drain: energy rate * distance
        double drained = stepKm * v.getVehicleType().getEnergyRatePercentPerKm().doubleValue();

        BigDecimal nextBattery = v.getBatteryLevel().subtract(BigDecimal.valueOf(drained));
        v.setBatteryLevel(nextBattery.signum() < 0 ? BigDecimal.ZERO : nextBattery.setScale(2, RoundingMode.HALF_UP));

        vehicleRepository.save(v);
        return false;
    }

    private void parkAtStation(Vehicle v, Station station, LocalDateTime now) {
        v.setCurrentLat(station.getLatitude());
        v.setCurrentLng(station.getLongitude());
        v.setStationId(station.getId());
        v.setTargetStationId(null);
        v.setLocationCode(station.getId().intValue());
        v.setCurrentSpeed(BigDecimal.ZERO);
        v.setPositionUpdatedAt(now);
        v.setSpeedUpdatedAt(now);
        // 返站默认在配送站充电：电量未满转 CHARGING 由 tick 补电，满电则待命
        v.setStatus(v.restingStatus());
        v.setStatusUpdatedAt(now);
        vehicleRepository.save(v);
    }
}
