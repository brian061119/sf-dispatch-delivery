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
    /** 配送中载具数（字段名保留 busyVehicles 以免影响既有前端，语义即 IN_DELIVERY） */
    private Long busyVehicles;
    private Long chargingVehicles;
    private Long faultVehicles;
    private Long offlineVehicles;
    private List<RecentOrderDto> recentOrders;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StationSummaryDto {
        private Long stationId;
        private String stationCode;
        private String name;
        private String address;
        private String contactPhone;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private Integer maxCapacity;
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
        private String statusLabel;
        private BigDecimal batteryLevel;
        private BigDecimal maxWeight;
        private BigDecimal cruiseSpeed;
        private BigDecimal enduranceMinutes;
        private BigDecimal maxDeliverableDistanceKm;
        /** 0=不在任何站点，1/2/3=位于对应站点 */
        private Integer locationCode;
        private BigDecimal currentSpeed;
        private LocalDateTime updatedAt;
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
