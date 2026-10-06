package com.wedelivery.repository;

import com.wedelivery.entity.Vehicle;
import com.wedelivery.entity.enums.VehicleStatus;
import com.wedelivery.entity.enums.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByStationIdAndVehicleTypeAndStatus(Long stationId, VehicleType vehicleType, VehicleStatus status);

    /** 站内某类型、状态属于给定集合的载具（派单候选池：待命 + 充电）。 */
    List<Vehicle> findByStationIdAndVehicleTypeAndStatusIn(Long stationId, VehicleType vehicleType, Collection<VehicleStatus> statuses);

    List<Vehicle> findByVehicleTypeAndStatus(VehicleType vehicleType, VehicleStatus status);

    List<Vehicle> findByStationId(Long stationId);

    Optional<Vehicle> findByVehicleCode(String vehicleCode);

    List<Vehicle> findByStatus(VehicleStatus status);

    // Pessimistic write lock query to find available vehicles during order dispatch/booking, locking eligible vehicles.
    // Ordered by batteryLevel ASC so the most depleted eligible vehicle is dispatched first (fleet balancing).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vehicle v WHERE v.stationId = :stationId " +
           "AND v.vehicleType = :vehicleType " +
           "AND v.status IN :statuses " +
           "AND v.batteryLevel >= :minBattery " +
           "AND v.maxWeight >= :weight " +
           "AND v.maxVolume >= :volume " +
           "ORDER BY v.batteryLevel ASC")
    List<Vehicle> findAvailableVehiclesForLock(
            @Param("stationId") Long stationId,
            @Param("vehicleType") VehicleType vehicleType,
            @Param("statuses") Collection<VehicleStatus> statuses,
            @Param("minBattery") BigDecimal minBattery,
            @Param("weight") BigDecimal weight,
            @Param("volume") BigDecimal volume
    );
}
