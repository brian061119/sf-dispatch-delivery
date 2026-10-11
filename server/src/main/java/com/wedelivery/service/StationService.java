package com.wedelivery.service;

import com.wedelivery.dto.AdminDashboardDto;
import com.wedelivery.dto.ImportResultDto;
import com.wedelivery.dto.StationAvailabilityDto;
import com.wedelivery.dto.StationInfoDto;
import com.wedelivery.dto.StationUpsertRequest;
import com.wedelivery.entity.Order;
import com.wedelivery.entity.Station;
import com.wedelivery.entity.Vehicle;
import com.wedelivery.entity.enums.VehicleStatus;
import com.wedelivery.entity.enums.VehicleType;
import com.wedelivery.repository.OrderRepository;
import com.wedelivery.repository.StationRepository;
import com.wedelivery.repository.UserRepository;
import com.wedelivery.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wedelivery.entity.enums.OrderStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StationService {

    private final StationRepository stationRepository;
    private final VehicleRepository vehicleRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    private static final int DEFAULT_DRONE_BAYS = 10;
    private static final int DEFAULT_ROBOT_BAYS = 15;

    // ------------------------------------------------------------------
    // Station basic information (Requirement 1, integrated with order system)
    // ------------------------------------------------------------------

    public List<StationInfoDto> listStationInfo() {
        return stationRepository.findAll().stream()
                .map(this::toStationInfoDto)
                .collect(Collectors.toList());
    }

    public StationInfoDto getStationInfo(Long stationId) {
        return toStationInfoDto(requireStation(stationId));
    }

    public StationInfoDto toStationInfoDto(Station s) {
        return StationInfoDto.builder()
                .stationId(s.getId())
                .stationCode(String.valueOf(s.getId()))
                .name(s.getName())
                .address(s.getAddress())
                .latitude(s.getLatitude())
                .longitude(s.getLongitude())
                .contactPhone(s.getContactPhone())
                .totalDroneBays(s.getTotalDroneBays())
                .totalRobotBays(s.getTotalRobotBays())
                .maxCapacity(maxCapacityOf(s))
                .build();
    }

    /** Maximum capacity = drone bays + robot bays */
    public static int maxCapacityOf(Station s) {
        int drones = s.getTotalDroneBays() != null ? s.getTotalDroneBays() : 0;
        int robots = s.getTotalRobotBays() != null ? s.getTotalRobotBays() : 0;
        return drones + robots;
    }

    public Station requireStation(Long stationId) {
        return stationRepository.findById(stationId)
                .orElseThrow(() -> new IllegalArgumentException("Station does not exist: " + stationId));
    }

    // ------------------------------------------------------------------
    // Station realtime availability (Requirement 2)
    // ------------------------------------------------------------------

    public StationAvailabilityDto getStationAvailability(Long stationId) {
        Station s = requireStation(stationId);
        List<Vehicle> all = vehicleRepository.findAll();
        return buildAvailability(s, all);
    }

    public List<StationAvailabilityDto> listStationAvailability() {
        List<Vehicle> all = vehicleRepository.findAll();
        return stationRepository.findAll().stream()
                .map(s -> buildAvailability(s, all))
                .collect(Collectors.toList());
    }

    /**
     * Aggregation metrics: vehicles grouped by station_id; "dispatchable" requires IDLE
     * and physically docked at station (location_code == station ID).
     * Maximum deliverable range is taken from max(endurance * speed) among dispatchable vehicles.
     */
    private StationAvailabilityDto buildAvailability(Station s, List<Vehicle> all) {

        int sid = s.getId().intValue();
        List<Vehicle> owned = all.stream()
                .filter(v -> v.getStationId() != null && v.getStationId() == sid)
                .collect(Collectors.toList());

        List<Vehicle> dispatchable = owned.stream()
                .filter(v -> v.getStatus() == VehicleStatus.IDLE)
                .filter(v -> v.getLocationCode() != null && v.getLocationCode() == sid)
                .collect(Collectors.toList());

        long droneCount = owned.stream().filter(v -> v.getVehicleType() == VehicleType.DRONE).count();
        long robotCount = owned.stream().filter(v -> v.getVehicleType() == VehicleType.ROBOT).count();
        long droneAvailable = dispatchable.stream().filter(v -> v.getVehicleType() == VehicleType.DRONE).count();
        long robotAvailable = dispatchable.stream().filter(v -> v.getVehicleType() == VehicleType.ROBOT).count();

        long onSite = all.stream()
                .filter(v -> v.getLocationCode() != null && v.getLocationCode() == sid)
                .count();

        int maxCapacity = maxCapacityOf(s);

        return StationAvailabilityDto.builder()
                .stationId(s.getId())
                .stationCode(String.valueOf(s.getId()))
                .name(s.getName())
                .maxCapacity(maxCapacity)
                .onSiteCount((int) onSite)
                .capacityRemaining(Math.max(0, maxCapacity - (int) onSite))
                .droneCount((int) droneCount)
                .robotCount((int) robotCount)
                .droneUnitsAvailable((int) droneAvailable)
                .robotUnitsAvailable((int) robotAvailable)
                .available(droneAvailable + robotAvailable > 0)
                .maxDroneRangeKm(maxRangeKm(dispatchable, VehicleType.DRONE))
                .maxRobotRangeKm(maxRangeKm(dispatchable, VehicleType.ROBOT))
                .build();
    }

    private BigDecimal maxRangeKm(List<Vehicle> dispatchable, VehicleType type) {
        return dispatchable.stream()
                .filter(v -> v.getVehicleType() == type)
                .map(Vehicle::getMaxDeliverableDistanceKm)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    // ------------------------------------------------------------------
    // Import: Station basic information
    // ------------------------------------------------------------------

    /**
     * Batch import station information with idempotent upsert keyed by ID.
     */
    @Transactional
    public ImportResultDto importStations(List<StationUpsertRequest> requests) {
        List<String> errors = new ArrayList<>();
        int created = 0;
        int updated = 0;

        if (requests == null || requests.isEmpty()) {
            return ImportResultDto.builder().created(0).updated(0).total(0).errors(errors).build();
        }

        for (int i = 0; i < requests.size(); i++) {
            StationUpsertRequest req = requests.get(i);
            String row = "#" + (i + 1);

            if (req == null || req.getId() == null) {
                errors.add(row + " Station ID is empty, skipped");
                continue;
            }
            if (req.getName() == null || req.getName().isBlank()) {
                errors.add(row + " (Station " + req.getId() + ") name is empty, skipped");
                continue;
            }
            if (req.getAddress() == null || req.getAddress().isBlank()) {
                errors.add(row + " (Station " + req.getId() + ") address is empty, skipped");
                continue;
            }
            if (req.getLatitude() == null || req.getLongitude() == null) {
                errors.add(row + " (Station " + req.getId() + ") latitude/longitude is empty, skipped");
                continue;
            }

            Optional<Station> existing = stationRepository.findById(req.getId());
            Station s = existing.orElseGet(() -> Station.builder().id(req.getId()).build());
            boolean isNew = existing.isEmpty();

            s.setName(req.getName());
            s.setAddress(req.getAddress());
            s.setLatitude(req.getLatitude());
            s.setLongitude(req.getLongitude());
            s.setTotalDroneBays(req.getTotalDroneBays() != null ? req.getTotalDroneBays() : DEFAULT_DRONE_BAYS);
            s.setTotalRobotBays(req.getTotalRobotBays() != null ? req.getTotalRobotBays() : DEFAULT_ROBOT_BAYS);
            s.setContactPhone(req.getContactPhone());

            stationRepository.save(s);
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }

        log.info("Station import finished: created={}, updated={}, errors={}", created, updated, errors.size());
        return ImportResultDto.builder()
                .created(created)
                .updated(updated)
                .total(requests.size())
                .errors(errors)
                .build();
    }

    // ------------------------------------------------------------------
    // Admin Operations Dashboard
    // ------------------------------------------------------------------


    public AdminDashboardDto getAdminDashboard() {
        List<Station> stations = stationRepository.findAll();
        List<Vehicle> allVehicles = vehicleRepository.findAll();
        List<Order> allOrders = orderRepository.findAllByOrderByCreatedAtDesc();

        long total = allVehicles.size();
        long idle = allVehicles.stream().filter(v -> v.getStatus() == VehicleStatus.IDLE).count();
        long inDelivery = allVehicles.stream().filter(v -> v.getStatus() == VehicleStatus.IN_DELIVERY).count();
        long charging = allVehicles.stream().filter(v -> v.getStatus() == VehicleStatus.CHARGING).count();
        long fault = allVehicles.stream().filter(v -> v.getStatus() == VehicleStatus.FAULT).count();
        long offline = allVehicles.stream().filter(v -> v.getStatus() == VehicleStatus.OFFLINE).count();

        Map<Long, Order> activeOrdersByVehicleId = allOrders.stream()
                .filter(o -> o.getVehicleId() != null &&
                        (o.getStatus() == OrderStatus.PICKING_UP || o.getStatus() == OrderStatus.IN_TRANSIT))
                .collect(Collectors.toMap(Order::getVehicleId, o -> o, (o1, o2) -> o1));

        List<AdminDashboardDto.StationSummaryDto> stationSummaries = stations.stream().map(s -> {
            List<Vehicle> sVehicles = allVehicles.stream()
                    .filter(v -> v.getStationId().equals(s.getId()))
                    .collect(Collectors.toList());

            List<Vehicle> dockedVehicles = sVehicles.stream()
                    .filter(v -> v.getStatus() != VehicleStatus.IN_DELIVERY)
                    .collect(Collectors.toList());
            int dockedCount = dockedVehicles.size();

            List<AdminDashboardDto.VehicleItemDto> vDtos = new ArrayList<>();
            for (Vehicle v : sVehicles) {
                BigDecimal lat;
                BigDecimal lng;
                Order activeOrder = activeOrdersByVehicleId.get(v.getId());

                if (v.getStatus() == VehicleStatus.IN_DELIVERY && v.getCurrentLat() != null && v.getCurrentLng() != null) {
                    lat = v.getCurrentLat();
                    lng = v.getCurrentLng();
                } else {
                    int dockIndex = dockedVehicles.indexOf(v);
                    if (dockIndex >= 0 && dockedCount > 1 && s.getLatitude() != null && s.getLongitude() != null) {
                        double angle = 2.0 * Math.PI * dockIndex / dockedCount;
                        double radius = 0.00035; // ~35 meters circular offset
                        lat = s.getLatitude().add(BigDecimal.valueOf(radius * Math.cos(angle)).setScale(7, RoundingMode.HALF_UP));
                        lng = s.getLongitude().add(BigDecimal.valueOf(radius * Math.sin(angle)).setScale(7, RoundingMode.HALF_UP));
                    } else {
                        lat = s.getLatitude();
                        lng = s.getLongitude();
                    }
                }

                AdminDashboardDto.ActiveOrderDto activeOrderDto = null;
                if (activeOrder != null) {
                    String custUsername = userRepository.findById(activeOrder.getUserId())
                            .map(u -> u.getUsername())
                            .orElse("Customer");
                    activeOrderDto = AdminDashboardDto.ActiveOrderDto.builder()
                            .orderNumber(activeOrder.getOrderNumber())
                            .status(activeOrder.getStatus())
                            .customerUsername(custUsername)
                            .pickupAddress(activeOrder.getPickupAddress())
                            .pickupLat(activeOrder.getPickupLat())
                            .pickupLng(activeOrder.getPickupLng())
                            .dropoffAddress(activeOrder.getDropoffAddress())
                            .dropoffLat(activeOrder.getDropoffLat())
                            .dropoffLng(activeOrder.getDropoffLng())
                            .packageWeight(activeOrder.getPackageWeight())
                            .packageVolume(activeOrder.getPackageVolume())
                            .finalPrice(activeOrder.getFinalPrice())
                            .build();
                }

                vDtos.add(AdminDashboardDto.VehicleItemDto.builder()
                        .id(v.getId())
                        .vehicleCode(v.getVehicleCode())
                        .vehicleType(v.getVehicleType())
                        .status(v.getStatus())
                        .statusLabel(v.getStatus().getLabel())
                        .batteryLevel(v.getBatteryLevel())
                        .maxWeight(v.getMaxWeight())
                        .cruiseSpeed(v.getCruiseSpeed())
                        .enduranceMinutes(v.getEnduranceMinutes())
                        .maxDeliverableDistanceKm(v.getMaxDeliverableDistanceKm())
                        .locationCode(v.getLocationCode())
                        .currentSpeed(v.getCurrentSpeed())
                        .currentLat(lat)
                        .currentLng(lng)
                        .stationId(s.getId())
                        .stationName(s.getName())
                        .activeOrder(activeOrderDto)
                        .updatedAt(v.getUpdatedAt())
                        .build());
            }

            return AdminDashboardDto.StationSummaryDto.builder()
                    .stationId(s.getId())
                    .stationCode(String.valueOf(s.getId()))
                    .name(s.getName())
                    .address(s.getAddress())
                    .latitude(s.getLatitude())
                    .longitude(s.getLongitude())
                    .contactPhone(s.getContactPhone())
                    .maxCapacity(maxCapacityOf(s))
                    .totalDroneBays(s.getTotalDroneBays())
                    .totalRobotBays(s.getTotalRobotBays())
                    .vehicles(vDtos)
                    .build();
        }).collect(Collectors.toList());

        List<AdminDashboardDto.RecentOrderDto> recentOrders = allOrders.stream().limit(15).map(o -> {
            String username = userRepository.findById(o.getUserId())
                    .map(u -> u.getUsername())
                    .orElse("Unknown");
            String sName = stations.stream()
                    .filter(s -> s.getId().equals(o.getStationId()))
                    .map(Station::getName)
                    .findFirst()
                    .orElse("Station " + o.getStationId());
            String vCode = allVehicles.stream()
                    .filter(v -> v.getId().equals(o.getVehicleId()))
                    .map(Vehicle::getVehicleCode)
                    .findFirst()
                    .orElse("N/A");

            return AdminDashboardDto.RecentOrderDto.builder()
                    .orderNumber(o.getOrderNumber())
                    .orderId(o.getOrderNumber())
                    .packageDescription(o.getPickupAddress() + " -> " + o.getDropoffAddress())
                    .customerUsername(username)
                    .stationName(sName)
                    .vehicleCode(vCode)
                    .vehicleType(o.getVehicleType())
                    .status(o.getStatus())
                    .finalPrice(o.getFinalPrice())
                    .estimatedCost(o.getFinalPrice())
                    .createdAt(o.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());

        return AdminDashboardDto.builder()
                .stations(stationSummaries)
                .totalVehicles(total)
                .idleVehicles(idle)
                .busyVehicles(inDelivery)
                .chargingVehicles(charging)
                .faultVehicles(fault)
                .offlineVehicles(offline)
                .recentOrders(recentOrders)
                .build();
    }
}
