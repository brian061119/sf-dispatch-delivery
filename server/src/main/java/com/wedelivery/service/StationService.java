package com.wedelivery.service;

import com.wedelivery.dto.AdminDashboardDto;
import com.wedelivery.entity.Order;
import com.wedelivery.entity.Station;
import com.wedelivery.entity.Vehicle;
import com.wedelivery.entity.enums.VehicleStatus;
import com.wedelivery.repository.OrderRepository;
import com.wedelivery.repository.StationRepository;
import com.wedelivery.repository.UserRepository;
import com.wedelivery.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationService {

    private final StationRepository stationRepository;
    private final VehicleRepository vehicleRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public List<Station> getAllStations() {
        return stationRepository.findAll();
    }

    public AdminDashboardDto getAdminDashboard() {
        List<Station> stations = stationRepository.findAll();
        List<Vehicle> allVehicles = vehicleRepository.findAll();
        List<Order> allOrders = orderRepository.findAllByOrderByCreatedAtDesc();

        long total = allVehicles.size();
        long idle = allVehicles.stream().filter(v -> v.getStatus() == VehicleStatus.IDLE).count();
        long busy = allVehicles.stream().filter(v -> v.getStatus() == VehicleStatus.BUSY).count();
        long charging = allVehicles.stream().filter(v -> v.getStatus() == VehicleStatus.CHARGING).count();

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
                            .batteryLevel(v.getBatteryLevel())
                            .maxWeight(v.getMaxWeight())
                            .cruiseSpeed(v.getCruiseSpeed())
                            .build()
            ).collect(Collectors.toList());

            return AdminDashboardDto.StationSummaryDto.builder()
                    .stationId(s.getId())
                    .name(s.getName())
                    .address(s.getAddress())
                    .latitude(s.getLatitude())
                    .longitude(s.getLongitude())
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
                .busyVehicles(busy)
                .chargingVehicles(charging)
                .recentOrders(recentOrders)
                .build();
    }
}
