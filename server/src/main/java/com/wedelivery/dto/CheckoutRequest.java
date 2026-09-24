package com.wedelivery.dto;

import com.wedelivery.entity.enums.PlanType;
import com.wedelivery.entity.enums.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutRequest {

    @NotNull
    private PlanType planType;

    @NotNull
    private VehicleType vehicleType;

    @NotNull
    private Long stationId;

    private Boolean isStationPickup = false;

    @NotBlank
    private String pickupAddress;

    @NotNull
    private BigDecimal pickupLat;

    @NotNull
    private BigDecimal pickupLng;

    @NotBlank
    private String dropoffAddress;

    @NotNull
    private BigDecimal dropoffLat;

    @NotNull
    private BigDecimal dropoffLng;

    @NotNull
    private BigDecimal packageWeight;

    @NotNull
    private BigDecimal packageVolume;

    @NotNull
    private BigDecimal totalDistance;

    @NotNull
    private BigDecimal originPrice;

    private BigDecimal discountAmount = BigDecimal.ZERO;

    @NotNull
    private BigDecimal finalPrice;

    private LocalDateTime scheduledStartTime;

    private LocalDateTime estimatedDeliveryTime;

    // 支付 Mock 字段
    @NotBlank
    private String cardNumber; // 模拟卡号：若末尾是 0000 则模拟扣款失败

    private String expDate;

    private String cvv;
}
