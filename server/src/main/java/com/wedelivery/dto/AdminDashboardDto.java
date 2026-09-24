package com.wedelivery.dto;

import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.entity.enums.VehicleStatus;
import com.wedelivery.entity.enums.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDashboardDto {
    private List<StationSummaryDto> stations;
    private Long totalVehicles;
    private Long idleVehicles;
    private Long busyVehicles;
    private Long chargingVehicles;
    private List<RecentOrderDto> recentOrders;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StationSummaryDto {
        private Long stationId;
        private String name;
        private String address;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private Integer totalDroneBays;
        private Integer totalRobotBays;
        private List<VehicleItemDto> vehicles;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VehicleItemDto {
        private Long id;
        private String vehicleCode;
        private VehicleType vehicleType;
        private VehicleStatus status;
        private BigDecimal batteryLevel;
        private BigDecimal maxWeight;
        private BigDecimal cruiseSpeed;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentOrderDto {
        private String orderNumber;
        private String customerUsername;
        private String stationName;
        private String vehicleCode;
        private VehicleType vehicleType;
        private OrderStatus status;
        private BigDecimal finalPrice;
        private LocalDateTime createdAt;
    }
}
