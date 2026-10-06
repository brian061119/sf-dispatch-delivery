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

    /** Location code: not at any station (in delivery / parked off-station) */
    public static final int LOCATION_NOT_AT_STATION = 0;

    /** Full battery level (%). */
    public static final BigDecimal FULL_BATTERY = new BigDecimal("100.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    /** Dynamic rerouting target station ID (reroutes vehicle to a closer station with available bays upon cancellation; resets to null on docking and updates stationId) */
    @Column(name = "target_station_id")
    private Long targetStationId;

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

    /** Full charge endurance (minutes) */
    @Column(name = "endurance_minutes", nullable = false, precision = 6, scale = 2)
    private BigDecimal enduranceMinutes;

    /** Location code: 0 = not at station, 1/2/3 = docked at corresponding station ID */
    @Column(name = "location_code", nullable = false)
    private Integer locationCode;

    @Column(name = "current_lat", precision = 10, scale = 7)
    private BigDecimal currentLat;

    @Column(name = "current_lng", precision = 10, scale = 7)
    private BigDecimal currentLng;

    /** Current speed in km/h, 0 when stationary */
    @Column(name = "current_speed", nullable = false, precision = 5, scale = 2)
    private BigDecimal currentSpeed;

    /** Timestamp of last position / location report */
    @Column(name = "position_updated_at")
    private LocalDateTime positionUpdatedAt;

    /** Timestamp of last status report */
    @Column(name = "status_updated_at")
    private LocalDateTime statusUpdatedAt;

    /** Timestamp of last speed report */
    @Column(name = "speed_updated_at")
    private LocalDateTime speedUpdatedAt;

    /** Timestamp of last update across any realtime metrics */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Maximum deliverable range (km) = endurance (hours) × cruise speed.
     * Used by station dispatching to determine if vehicle can cover the trip.
     */
    public BigDecimal getMaxDeliverableDistanceKm() {
        if (enduranceMinutes == null || cruiseSpeed == null) {
            return BigDecimal.ZERO;
        }
        return enduranceMinutes.divide(new BigDecimal("60"), 4, RoundingMode.HALF_UP)
                .multiply(cruiseSpeed)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Returns true if vehicle is docked at a station (location code 1/2/3) */
    public boolean isAtStation() {
        return locationCode != null && locationCode > LOCATION_NOT_AT_STATION;
    }

    /**
     * 停回站点后的默认状态：电量未满即 CHARGING（由模拟器逐 tick 补电，充满再转 IDLE），满电则直接 IDLE。
     * 充电需要时间，故返站不会瞬间回满待命。
     */
    public VehicleStatus restingStatus() {
        return batteryLevel != null && batteryLevel.compareTo(FULL_BATTERY) < 0
                ? VehicleStatus.CHARGING
                : VehicleStatus.IDLE;
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
