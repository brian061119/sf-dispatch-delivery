package com.wedelivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuoteRequest {

    private String pickupAddress;

    @NotNull
    private BigDecimal pickupLat;

    @NotNull
    private BigDecimal pickupLng;

    @NotNull
    private String dropoffAddress;

    @NotNull
    private BigDecimal dropoffLat;

    @NotNull
    private BigDecimal dropoffLng;

    private Boolean isStationPickup = false;

    private Long stationId; // 可选自投站点

    @NotNull
    private BigDecimal packageWeight; // kg

    @NotNull
    private BigDecimal packageVolume; // m³
}
