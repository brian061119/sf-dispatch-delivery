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
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByStationIdAndVehicleTypeAndStatus(Long stationId, VehicleType vehicleType, VehicleStatus status);

    List<Vehicle> findByStationId(Long stationId);

    // 下单时的原子悲观锁查询可用车辆，锁定首辆满足条件的载具
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vehicle v WHERE v.stationId = :stationId " +
           "AND v.vehicleType = :vehicleType " +
           "AND v.status = :status " +
           "AND v.batteryLevel >= :minBattery " +
           "AND v.maxWeight >= :weight " +
           "AND v.maxVolume >= :volume " +
           "ORDER BY v.batteryLevel DESC")
    List<Vehicle> findAvailableVehiclesForLock(
            @Param("stationId") Long stationId,
            @Param("vehicleType") VehicleType vehicleType,
            @Param("status") VehicleStatus status,
            @Param("minBattery") BigDecimal minBattery,
            @Param("weight") BigDecimal weight,
            @Param("volume") BigDecimal volume
    );
}
