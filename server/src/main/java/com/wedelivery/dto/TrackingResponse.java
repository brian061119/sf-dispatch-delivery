package com.wedelivery.dto;

import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.entity.enums.TrackingStage;
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
public class TrackingResponse {
    private String orderNumber;
    private OrderStatus orderStatus;
    private VehicleType vehicleType;
    private String vehicleCode;
    private TrackingStage currentStage;
    private String currentStageDescription;
    private BigDecimal currentLat;
    private BigDecimal currentLng;
    private BigDecimal progressPercent; // 0.0 ~ 100.0
    private Integer etaMinutesRemaining;
    private List<TrackingEventDto> events;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TrackingEventDto {
        private TrackingStage stage;
        private String statusDescription;
        private BigDecimal eventLat;
        private BigDecimal eventLng;
        private LocalDateTime eventTime;
    }
}
