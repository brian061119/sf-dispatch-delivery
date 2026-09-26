package com.wedelivery.dto;

import com.wedelivery.entity.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutResponse {
    private String orderNumber;
    private String trackingCode;
    private OrderStatus status;
    private String transactionNo;
    private String assignedVehicleCode;
    private LocalDateTime estimatedDeliveryTime;
    private String message;
}
