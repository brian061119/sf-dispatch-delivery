package com.wedelivery.entity;

import com.wedelivery.entity.enums.VehicleStatus;
import com.wedelivery.entity.enums.VehicleType;
import lombok.*;

import javax.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

    /** 位置编码：不在任何站点（配送途中 / 停放于站外） */
    public static final int LOCATION_NOT_AT_STATION = 0;

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

    /** 满电续航时间（分钟） */
    @Column(name = "endurance_minutes", nullable = false, precision = 6, scale = 2)
    private BigDecimal enduranceMinutes;

    /** 位置编码：0=不在任何站点，1/2/3=位于对应站点 id */
    @Column(name = "location_code", nullable = false)
    private Integer locationCode;

    @Column(name = "current_lat", precision = 10, scale = 7)
    private BigDecimal currentLat;

    @Column(name = "current_lng", precision = 10, scale = 7)
    private BigDecimal currentLng;

    /** 当前速度 km/h，静止为 0 */
    @Column(name = "current_speed", nullable = false, precision = 5, scale = 2)
    private BigDecimal currentSpeed;

    /** 位置（经纬度 / 位置编码）最后上报时间 */
    @Column(name = "position_updated_at")
    private LocalDateTime positionUpdatedAt;

    /** 状态最后上报时间 */
    @Column(name = "status_updated_at")
    private LocalDateTime statusUpdatedAt;

    /** 速度最后上报时间 */
    @Column(name = "speed_updated_at")
    private LocalDateTime speedUpdatedAt;

    /** 任意实时信息最后上报时间 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 最大可配送路程（km）= 续航时间 × 默认最大速度。
     * 需求「最大速度、续航接入站点信息，据可配送路程决定是否配送」的计算基准。
     */
    public BigDecimal getMaxDeliverableDistanceKm() {
        if (enduranceMinutes == null || cruiseSpeed == null) {
            return BigDecimal.ZERO;
        }
        return enduranceMinutes.divide(new BigDecimal("60"), 4, RoundingMode.HALF_UP)
                .multiply(cruiseSpeed)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** 是否停驻在某个站点（位置编码 1/2/3） */
    public boolean isAtStation() {
        return locationCode != null && locationCode > LOCATION_NOT_AT_STATION;
    }

    @PrePersist
    @PreUpdate
    protected void onSave() {
        if (status == null) {
            status = VehicleStatus.IDLE;
        }
        if (batteryLevel == null) {
            batteryLevel = new BigDecimal("100.00");
        }
        if (vehicleType != null) {
            if (maxWeight == null) {
                maxWeight = vehicleType.getDefaultMaxWeight();
            }
            if (maxVolume == null) {
                maxVolume = vehicleType.getDefaultMaxVolume();
            }
            if (cruiseSpeed == null) {
                cruiseSpeed = vehicleType.getDefaultCruiseSpeed();
            }
            if (enduranceMinutes == null) {
                enduranceMinutes = vehicleType.getDefaultEnduranceMinutes();
            }
        }
        if (locationCode == null) {
            locationCode = LOCATION_NOT_AT_STATION;
        }
        if (currentSpeed == null) {
            currentSpeed = BigDecimal.ZERO;
        }
        LocalDateTime now = LocalDateTime.now();
        if (positionUpdatedAt == null) {
            positionUpdatedAt = now;
        }
        if (statusUpdatedAt == null) {
            statusUpdatedAt = now;
        }
        if (speedUpdatedAt == null) {
            speedUpdatedAt = now;
        }
        updatedAt = now;
    }
}
