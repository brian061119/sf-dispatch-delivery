package com.wedelivery.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.entity.enums.PlanType;
import com.wedelivery.entity.enums.VehicleType;
import lombok.*;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 64)
    private String orderNumber;

    // Public tracking code: random unguessable token allowing guest users read-only tracking access
    @Column(name = "tracking_code", nullable = false, unique = true, length = 32)
    private String trackingCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // JPA association only: ignored in JSON serialization to avoid proxy issues and credential exposure
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    // JPA association only: ignored in JSON serialization to avoid proxy issues and credential exposure
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", insertable = false, updatable = false)
    private Station station;

    @Column(name = "vehicle_id")
    private Long vehicleId;

    // JPA association only: ignored in JSON serialization to avoid proxy issues and credential exposure
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", insertable = false, updatable = false)
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 20)
    private PlanType planType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "is_station_pickup", nullable = false)
    private Boolean isStationPickup;

    @Column(name = "pickup_address", nullable = false)
    private String pickupAddress;

    @Column(name = "pickup_lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal pickupLat;

    @Column(name = "pickup_lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal pickupLng;

    @Column(name = "dropoff_address", nullable = false)
    private String dropoffAddress;

    @Column(name = "dropoff_lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal dropoffLat;

    @Column(name = "dropoff_lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal dropoffLng;

    @Column(name = "package_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal packageWeight;

    @Column(name = "package_volume", nullable = false, precision = 5, scale = 2)
    private BigDecimal packageVolume;

    @Column(name = "total_distance", nullable = false, precision = 8, scale = 2)
    private BigDecimal totalDistance;

    @Column(name = "origin_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal originPrice;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "final_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal finalPrice;

    @Column(name = "scheduled_start_time", nullable = false)
    private LocalDateTime scheduledStartTime;

    @Column(name = "estimated_delivery_time", nullable = false)
    private LocalDateTime estimatedDeliveryTime;

    @Column(name = "actual_delivery_time")
    private LocalDateTime actualDeliveryTime;

    @Column(name = "cancellation_fee", precision = 10, scale = 2)
    private BigDecimal cancellationFee;

    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "return_station_id")
    private Long returnStationId;

    @Column(name = "has_been_modified", nullable = false)
    private Boolean hasBeenModified;

    @Column(name = "modified_count", nullable = false)
    private Integer modifiedCount;

    @Transient
    private Boolean droneUpgradeAvailable;

    @Transient
    private Integer maxModificationsAllowed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = OrderStatus.PENDING_PAYMENT;
        }
        if (discountAmount == null) {
            discountAmount = BigDecimal.ZERO;
        }
        if (isStationPickup == null) {
            isStationPickup = false;
        }
        if (hasBeenModified == null) {
            hasBeenModified = false;
        }
        if (modifiedCount == null) {
            modifiedCount = 0;
        }
        createdAt = LocalDateTime.now();
    }
}
