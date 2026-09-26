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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
    // 站点基础信息（需求 1，接入订单系统）
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

    /** 最大容量 = 无人机坪位 + 机器人泊位（不额外建列，避免与分类型泊位口径不一致） */
    public static int maxCapacityOf(Station s) {
        int drones = s.getTotalDroneBays() != null ? s.getTotalDroneBays() : 0;
        int robots = s.getTotalRobotBays() != null ? s.getTotalRobotBays() : 0;
        return drones + robots;
    }

    public Station requireStation(Long stationId) {
        return stationRepository.findById(stationId)
                .orElseThrow(() -> new IllegalArgumentException("站点不存在: " + stationId));
    }

    // ------------------------------------------------------------------
    // 站点实时信息（需求 2）
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
     * 统计口径：数量按 station_id 归属统计；「可调度」额外要求载具 IDLE 且物理停驻本站
     * （location_code == 站点编号），因为停在别处或正在配送的机器无法立刻接单。
     * 站点级最大可配送路程取本站可调度载具「续航 × 最大速度」的最大值，
     * 供调用方判断本站能否覆盖到目标送货点。
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
    // 导入：站点基础信息
    // ------------------------------------------------------------------

    /**
     * 批量导入站点基础信息，按 id（即站点编号）幂等 upsert。
     * 逐条处理，单条非法只记入 errors，不影响其余条目。
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
                errors.add(row + " 站点编号(id)为空，已跳过");
                continue;
            }
            if (req.getName() == null || req.getName().isBlank()) {
                errors.add(row + " (站点 " + req.getId() + ") 名称为空，已跳过");
                continue;
            }
            if (req.getAddress() == null || req.getAddress().isBlank()) {
                errors.add(row + " (站点 " + req.getId() + ") 地址为空，已跳过");
                continue;
            }
            if (req.getLatitude() == null || req.getLongitude() == null) {
                errors.add(row + " (站点 " + req.getId() + ") 经纬度为空，已跳过");
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
    // 运营站控大盘
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

        List<AdminDashboardDto.StationSummaryDto> stationSummaries = stations.stream().map(s -> {
            List<Vehicle> sVehicles = allVehicles.stream()
                    .filter(v -> v.getStationId().equals(s.getId()))
                    .collect(Collectors.toList());

            List<AdminDashboardDto.VehicleItemDto> vDtos = sVehicles.stream().map(v ->
                    AdminDashboardDto.VehicleItemDto.builder()
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
                            .updatedAt(v.getUpdatedAt())
                            .build()
            ).collect(Collectors.toList());

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
                    .customerUsername(username)
                    .stationName(sName)
                    .vehicleCode(vCode)
                    .vehicleType(o.getVehicleType())
                    .status(o.getStatus())
                    .finalPrice(o.getFinalPrice())
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
