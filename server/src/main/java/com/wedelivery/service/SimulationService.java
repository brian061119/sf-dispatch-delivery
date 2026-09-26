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
 * demo 模拟器：代替真实的「机器接入 + 地图接入」心跳，把载具的实时信息推进起来。
 *
 * 位置推进不重新实现插值数学，而是复用 {@link TrackingService#trackOrder}——
 * 追踪接口本身已经把插值坐标同步回载具（见 TrackingService 的实时信息同步段），
 * 因此模拟器只需触发它，保证载具实时信息与追踪页面永远一致。
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

    /** 一次 tick 代表的模拟行驶时长（分钟） */
    private static final double TICK_MINUTES = 1.0;
    /** 充电速率：每次 tick 补充的电量百分比 */
    private static final BigDecimal CHARGE_RATE_PER_TICK = new BigDecimal("10.00");
    /** 视为已抵达站点的距离阈值 km，与 VehicleService 的停驻半径保持一致口径 */
    private static final double ARRIVE_RADIUS_KM = 0.15;

    private static final List<OrderStatus> ACTIVE_ORDER_STATUSES =
            Arrays.asList(OrderStatus.PAID, OrderStatus.PICKING_UP, OrderStatus.IN_TRANSIT);

    @Transactional
    public SimulationTickResponse tick() {
        LocalDateTime now = LocalDateTime.now();
        Set<Long> movingVehicleIds = new HashSet<>();

        // 1. 推进所有在途订单：复用追踪插值，坐标会被写回载具实时信息
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
                // 2. 无在途订单却仍是「配送中」的载具：视为任务已结束但未收到回站上报，逐步返站
                if (v.getId() != null && !movingVehicleIds.contains(v.getId())) {
                    if (returnToStation(v, stations.get(v.getStationId()), now)) {
                        returned++;
                    }
                }
            } else if (v.getStatus() == VehicleStatus.CHARGING) {
                // 3. 补电：满电后自动转入待命
                BigDecimal chargedLevel = v.getBatteryLevel().add(CHARGE_RATE_PER_TICK);
                if (chargedLevel.compareTo(new BigDecimal("100.00")) >= 0) {
                    v.setBatteryLevel(new BigDecimal("100.00"));
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
     * 让一台脱离任务的载具朝所属站点推进一个 tick。
     * @return 本次是否已抵达站点并归位（转为待命）
     */
    private boolean returnToStation(Vehicle v, Station station, LocalDateTime now) {
        if (station == null) {
            return false;
        }

        double sLat = station.getLatitude().doubleValue();
        double sLng = station.getLongitude().doubleValue();

        // 位置未知：直接归位，避免出现「配送中但无坐标」的空窗
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

        // 空载返程能耗：单位能耗率 × 里程
        double drained = stepKm * v.getVehicleType().getEnergyRatePercentPerKm().doubleValue();
        BigDecimal nextBattery = v.getBatteryLevel().subtract(BigDecimal.valueOf(drained));
        v.setBatteryLevel(nextBattery.signum() < 0 ? BigDecimal.ZERO : nextBattery.setScale(2, RoundingMode.HALF_UP));

        vehicleRepository.save(v);
        return false;
    }

    private void parkAtStation(Vehicle v, Station station, LocalDateTime now) {
        v.setCurrentLat(station.getLatitude());
        v.setCurrentLng(station.getLongitude());
        v.setLocationCode(station.getId().intValue());
        v.setCurrentSpeed(BigDecimal.ZERO);
        v.setPositionUpdatedAt(now);
        v.setSpeedUpdatedAt(now);
        v.setStatus(VehicleStatus.IDLE);
        v.setStatusUpdatedAt(now);
        vehicleRepository.save(v);
    }
}
