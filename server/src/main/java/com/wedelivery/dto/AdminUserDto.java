package com.wedelivery.dto;

import com.wedelivery.entity.enums.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin console user views (GET /api/admin/users, GET /api/admin/users/{id}).
 * Built field by field from the User entity so passwordHash can never leak.
 */
public class AdminUserDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private Long id;
        private String username;
        private String firstName;
        private String lastName;
        private String email;
        private String role;
        private Boolean isVip;
        private LocalDateTime vipExpireAt;
        private LocalDateTime createdAt;
        private long orderCount;
        /** Orders not yet DELIVERED or CANCELLED. */
        private long activeOrderCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Detail {
        private Summary user;
        /** Newest first. */
        private List<OrderItem> orders;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem {
        private String orderNumber;
        private String trackingCode;
        /** Contract 4-state: PENDING | IN_TRANSIT | DELIVERED | CANCELLED. */
        private String status;
        /** Raw 6-state OrderStatus. */
        private String detailStatus;
        private VehicleType vehicleType;
        private String pickupAddress;
        private String dropoffAddress;
        private BigDecimal finalPrice;
        private LocalDateTime createdAt;
        private LocalDateTime actualDeliveryTime;
    }
}
