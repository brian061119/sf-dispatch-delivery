package com.wedelivery.dto;

import com.wedelivery.entity.enums.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderUpdateRequest {
    // Pickup modifications
    private String pickupAddress;
    private BigDecimal pickupLat;
    private BigDecimal pickupLng;

    // Destination modifications
    private String dropoffAddress;
    private BigDecimal dropoffLat;
    private BigDecimal dropoffLng;

    // Package specification modifications
    private String packageDescription;
    private BigDecimal packageWeight;
    private BigDecimal packageLengthCm;
    private BigDecimal packageWidthCm;
    private BigDecimal packageHeightCm;

    // Delivery mode & speed upgrade modifications
    private VehicleType vehicleType;
    private Boolean upgradeToDrone;

    // Payment credential for processing price difference if surcharge applies
    private String cardNumber;
    private String paymentMethodId;
}
