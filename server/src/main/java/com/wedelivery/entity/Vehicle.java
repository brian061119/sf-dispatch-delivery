package com.wedelivery.entity;

import com.wedelivery.entity.enums.VehicleStatus;
import com.wedelivery.entity.enums.VehicleType;
import lombok.*;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", insertable = false, updatable = false)
    private Station station;

    @Column(name = "vehicle_code", nullable = false, unique = true, length = 32)
    private String vehicleCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleStatus status;

    @Column(name = "battery_level", nullable = false, precision = 5, scale = 2)
    private BigDecimal batteryLevel;

    @Column(name = "max_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxWeight;

    @Column(name = "max_volume", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxVolume;

    @Column(name = "cruise_speed", nullable = false, precision = 5, scale = 2)
    private BigDecimal cruiseSpeed;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        if (status == null) {
            status = VehicleStatus.IDLE;
        }
        if (batteryLevel == null) {
            batteryLevel = new BigDecimal("100.00");
        }
        updatedAt = LocalDateTime.now();
    }
}
