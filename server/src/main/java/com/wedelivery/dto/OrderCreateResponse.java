package com.wedelivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreateResponse {
    private String orderId;
    private String trackingCode;
    private String status;
    private Integer estimatedTimeMinutes;
    private BigDecimal estimatedCost;
    private String transactionNo;
    private String assignedVehicleCode;
    private String message;
}
