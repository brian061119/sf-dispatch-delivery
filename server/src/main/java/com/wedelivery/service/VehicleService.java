package com.wedelivery.service;

import com.wedelivery.dto.ImportResultDto;
import com.wedelivery.dto.VehicleInfoDto;
import com.wedelivery.dto.VehicleLocationRequest;
import com.wedelivery.dto.VehicleRealtimeDto;
import com.wedelivery.dto.VehicleTelemetryRequest;
import com.wedelivery.dto.VehicleUpsertRequest;
import com.wedelivery.entity.Station;
import com.wedelivery.entity.Vehicle;
import com.wedelivery.entity.enums.VehicleStatus;
import com.wedelivery.repository.StationRepository;
import com.wedelivery.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 机器（机器人 / 无人机）基础信息与实时信息的唯一入口。
 *
 * 接入方向（外部 → 本模块）：机器心跳（状态/电量/速度/续航）、地图位置（经纬度）。
 * 供数方向（本模块 → 外部）：基础信息给订单系统，实时信息给实时追踪系统。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final StationRepository stationRepository;
    private final RouteService routeService;

    /** 判定「停在站点」的距离阈值：与站点直线距离 150m 内即视为停驻该站 */
    private static final double STATION_DWELL_RADIUS_KM = 0.15;
    private static final String NOT_AT_STATION_LABEL = "不在任何站点";

    // ------------------------------------------------------------------
    // 查询：基础信息（需求 3，接入订单系统）
    // ------------------------------------------------------------------

    public List<VehicleInfoDto> listVehicleInfo() {
        return vehicleRepository.findAll().stream()
                .map(this::toInfoDto)
                .collect(Collectors.toList());
    }

    public VehicleInfoDto getVehicleInfo(String vehicleCode) {
        return toInfoDto(requireVehicle(vehicleCode));
    }

    public VehicleInfoDto toInfoDto(Vehicle v) {
        return VehicleInfoDto.builder()
                .id(v.getId())
                .vehicleCode(v.getVehicleCode())
                .vehicleType(v.getVehicleType().name())
                .vehicleTypeLabel(v.getVehicleType().getLabel())
                .stationId(v.getStationId())
                .maxWeight(v.getMaxWeight())
                .maxVolume(v.getMaxVolume())
                .cruiseSpeed(v.getCruiseSpeed())
                .enduranceMinutes(v.getEnduranceMinutes())
                .maxDeliverableDistanceKm(v.getMaxDeliverableDistanceKm())
                .build();
    }

    // ------------------------------------------------------------------
    // 查询：实时信息（需求 4，接入实时追踪系统）
    // ------------------------------------------------------------------

    public List<VehicleRealtimeDto> listVehicleRealtime() {
        Map<Long, String> stationNames = stationNameMap();
        return vehicleRepository.findAll().stream()
                .map(v -> toRealtimeDto(v, stationNames))
                .collect(Collectors.toList());
    }

    public VehicleRealtimeDto getVehicleRealtime(String vehicleCode) {
        return toRealtimeDto(requireVehicle(vehicleCode), stationNameMap());
    }

    public VehicleRealtimeDto toRealtimeDto(Vehicle v, Map<Long, String> stationNames) {
        Integer code = v.getLocationCode() == null ? Vehicle.LOCATION_NOT_AT_STATION : v.getLocationCode();
        return VehicleRealtimeDto.builder()
                .vehicleCode(v.getVehicleCode())
                .vehicleType(v.getVehicleType().name())
                .vehicleTypeLabel(v.getVehicleType().getLabel())
                .status(v.getStatus().name())
                .statusLabel(v.getStatus().getLabel())
                .locationCode(code)
                .locationLabel(code > Vehicle.LOCATION_NOT_AT_STATION
                        ? stationNames.getOrDefault(code.longValue(), "站点 " + code)
                        : NOT_AT_STATION_LABEL)
                .currentLat(v.getCurrentLat())
                .currentLng(v.getCurrentLng())
                .currentSpeed(v.getCurrentSpeed())
                .batteryLevel(v.getBatteryLevel())
                .positionUpdatedAt(v.getPositionUpdatedAt())
                .statusUpdatedAt(v.getStatusUpdatedAt())
                .speedUpdatedAt(v.getSpeedUpdatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }

    public Map<Long, String> stationNameMap() {
        Map<Long, String> names = new HashMap<>();
        for (Station s : stationRepository.findAll()) {
            names.put(s.getId(), s.getName());
        }
        return names;
    }

    // ------------------------------------------------------------------
    // 接入：机器心跳（状态与更新）
    // ------------------------------------------------------------------

    /**
     * 应用机器上报表。字段可缺省，只更新本次实际上报的项，并为各项打上对应的更新时刻，
     * 以便满足「更新时间（位置、状态、速度）分列」的要求。
     */
    @Transactional
    public VehicleRealtimeDto applyTelemetry(String vehicleCode, VehicleTelemetryRequest req) {
        Vehicle v = requireVehicle(vehicleCode);
        LocalDateTime now = LocalDateTime.now();

        if (req.getStatus() != null) {
            v.setStatus(req.getStatus());
            v.setStatusUpdatedAt(now);
        }
        if (req.getBatteryLevel() != null) {
            v.setBatteryLevel(req.getBatteryLevel());
        }
        if (req.getCurrentSpeed() != null) {
            v.setCurrentSpeed(req.getCurrentSpeed());
            v.setSpeedUpdatedAt(now);
        }
        if (req.getEnduranceMinutes() != null) {
            v.setEnduranceMinutes(req.getEnduranceMinutes());
        }

        // 状态非配送中时，不可能仍在高速行驶：归零避免出现「待命但速度 14km/h」的脏数据
        if (v.getStatus() != VehicleStatus.IN_DELIVERY && v.getCurrentSpeed().signum() > 0) {
            v.setCurrentSpeed(BigDecimal.ZERO);
            v.setSpeedUpdatedAt(now);
        }

        // saveAndFlush 让 @PreUpdate 在返回视图前落地，保证响应里的 updatedAt 是本次上报时刻
        vehicleRepository.saveAndFlush(v);
        log.info("Telemetry applied for {}: status={}, battery={}, speed={}",
                vehicleCode, v.getStatus(), v.getBatteryLevel(), v.getCurrentSpeed());
        return toRealtimeDto(v, stationNameMap());
    }

    // ------------------------------------------------------------------
    // 接入：地图位置（经纬度 → 推导位置编码）
    // ------------------------------------------------------------------

    @Transactional
    public VehicleRealtimeDto applyLocation(String vehicleCode, VehicleLocationRequest req) {
        Vehicle v = requireVehicle(vehicleCode);
        LocalDateTime now = LocalDateTime.now();

        v.setCurrentLat(req.getLatitude());
        v.setCurrentLng(req.getLongitude());
        v.setLocationCode(deriveLocationCode(req.getLatitude(), req.getLongitude()));
        v.setPositionUpdatedAt(now);

        if (req.getCurrentSpeed() != null) {
            v.setCurrentSpeed(req.getCurrentSpeed());
            v.setSpeedUpdatedAt(now);
        }

        vehicleRepository.saveAndFlush(v);
        log.info("Location applied for {}: lat={}, lng={}, locationCode={}",
                vehicleCode, req.getLatitude(), req.getLongitude(), v.getLocationCode());
        return toRealtimeDto(v, stationNameMap());
    }

    /**
     * 位置编码推导：落在某站点 150m 内则返回该站点编号，否则返回 0（不在任何站点）。
     */
    public int deriveLocationCode(BigDecimal lat, BigDecimal lng) {
        if (lat == null || lng == null) {
            return Vehicle.LOCATION_NOT_AT_STATION;
        }
        double dLat = lat.doubleValue();
        double dLng = lng.doubleValue();

        Optional<Station> nearest = stationRepository.findAll().stream()
                .min(Comparator.comparingDouble(s -> routeService.calculateStraightDistance(
                        s.getLatitude().doubleValue(), s.getLongitude().doubleValue(), dLat, dLng)));

        if (nearest.isEmpty()) {
            return Vehicle.LOCATION_NOT_AT_STATION;
        }
        Station s = nearest.get();
        double distance = routeService.calculateStraightDistance(
                s.getLatitude().doubleValue(), s.getLongitude().doubleValue(), dLat, dLng);

        return distance <= STATION_DWELL_RADIUS_KM ? s.getId().intValue() : Vehicle.LOCATION_NOT_AT_STATION;
    }

    // ------------------------------------------------------------------
    // 导入：机器基础信息
    // ------------------------------------------------------------------

    /**
     * 批量导入机器基础信息，按 vehicleCode 幂等 upsert。
     * 逐条处理，单条非法只记入 errors，不影响其余条目。
     * 新登记的载具默认停泊在所属站点、待命满电。
     */
    @Transactional
    public ImportResultDto importVehicles(List<VehicleUpsertRequest> requests) {
        List<String> errors = new ArrayList<>();
        int created = 0;
        int updated = 0;

        if (requests == null || requests.isEmpty()) {
            return ImportResultDto.builder().created(0).updated(0).total(0).errors(errors).build();
        }

        Map<Long, Station> stations = new HashMap<>();
        for (Station s : stationRepository.findAll()) {
            stations.put(s.getId(), s);
        }

        for (int i = 0; i < requests.size(); i++) {
            VehicleUpsertRequest req = requests.get(i);
            String row = "#" + (i + 1);

            if (req == null || req.getVehicleCode() == null || req.getVehicleCode().isBlank()) {
                errors.add(row + " 载具编号为空，已跳过");
                continue;
            }
            if (req.getVehicleType() == null) {
                errors.add(row + " (" + req.getVehicleCode() + ") 载具类型为空，已跳过");
                continue;
            }
            Station station = req.getStationId() == null ? null : stations.get(req.getStationId());
            if (station == null) {
                errors.add(row + " (" + req.getVehicleCode() + ") 所属站点不存在: " + req.getStationId());
                continue;
            }

            Optional<Vehicle> existing = vehicleRepository.findByVehicleCode(req.getVehicleCode());
            Vehicle v = existing.orElseGet(() -> Vehicle.builder()
                    .vehicleCode(req.getVehicleCode())
                    .build());
            boolean isNew = existing.isEmpty();

            v.setVehicleType(req.getVehicleType());
            v.setStationId(station.getId());
            v.setMaxWeight(req.getMaxWeight() != null ? req.getMaxWeight() : req.getVehicleType().getDefaultMaxWeight());
            v.setMaxVolume(req.getMaxVolume() != null ? req.getMaxVolume() : req.getVehicleType().getDefaultMaxVolume());
            v.setCruiseSpeed(req.getCruiseSpeed() != null ? req.getCruiseSpeed() : req.getVehicleType().getDefaultCruiseSpeed());
            v.setEnduranceMinutes(req.getEnduranceMinutes() != null
                    ? req.getEnduranceMinutes()
                    : req.getVehicleType().getDefaultEnduranceMinutes());
            v.setStatus(req.getStatus() != null ? req.getStatus() : VehicleStatus.IDLE);
            v.setBatteryLevel(req.getBatteryLevel() != null ? req.getBatteryLevel() : new BigDecimal("100.00"));
            if (isNew) {
                // 新登记的机器视为停放于所属站点
                v.setLocationCode(station.getId().intValue());
                v.setCurrentLat(station.getLatitude());
                v.setCurrentLng(station.getLongitude());
                v.setCurrentSpeed(BigDecimal.ZERO);
            }

            vehicleRepository.save(v);
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }

        log.info("Vehicle import finished: created={}, updated={}, errors={}", created, updated, errors.size());
        return ImportResultDto.builder()
                .created(created)
                .updated(updated)
                .total(requests.size())
                .errors(errors)
                .build();
    }

    // ------------------------------------------------------------------

    public Vehicle requireVehicle(String vehicleCode) {
        return vehicleRepository.findByVehicleCode(vehicleCode)
                .orElseThrow(() -> new IllegalArgumentException("机器不存在: " + vehicleCode));
    }

    /** 供调度与模拟器复用的「入库 + 返回实时视图」 */
    @Transactional
    public VehicleRealtimeDto saveAndView(Vehicle v) {
        vehicleRepository.saveAndFlush(v);
        return toRealtimeDto(v, stationNameMap());
    }
}
