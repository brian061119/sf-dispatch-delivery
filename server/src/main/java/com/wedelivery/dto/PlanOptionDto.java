package com.wedelivery.dto;

import com.wedelivery.entity.enums.PlanType;
import com.wedelivery.entity.enums.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanOptionDto {
    private PlanType planType;
    private VehicleType vehicleType;
    private Long stationId;
    private String stationName;
    private BigDecimal totalDistance; // km
    private Integer estimatedMinutes;
    private BigDecimal originPrice;
    private BigDecimal discountAmount;
    private BigDecimal finalPrice;
    private LocalDateTime scheduledStartTime;
    private LocalDateTime estimatedDeliveryTime;
    private String vehicleCode;
    private String description;
}
