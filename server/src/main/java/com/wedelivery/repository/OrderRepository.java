package com.wedelivery.repository;

import com.wedelivery.entity.Order;
import com.wedelivery.entity.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);
    Optional<Order> findByTrackingCode(String trackingCode);
    /**
     * Row-locked lookups for the tracking poll. Tracking writes milestones, order status and vehicle
     * telemetry, so concurrent polls (client refreshes + scheduler tick) must run one at a time per order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberForUpdate(@Param("orderNumber") String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.trackingCode = :trackingCode")
    Optional<Order> findByTrackingCodeForUpdate(@Param("trackingCode") String trackingCode);

    /** True when a newer, non-cancelled order has since been assigned the same vehicle. */
    boolean existsByVehicleIdAndIdGreaterThanAndStatusNot(Long vehicleId, Long id, OrderStatus status);

    boolean existsByTrackingCode(String trackingCode);
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Order> findAllByOrderByCreatedAtDesc();
    List<Order> findByStatusIn(Collection<OrderStatus> statuses);

    /** Admin user list: one row per user = [userId, total orders, orders whose status is in activeStatuses]. */
    @Query("SELECT o.userId, COUNT(o), SUM(CASE WHEN o.status IN :activeStatuses THEN 1 ELSE 0 END) "
            + "FROM Order o GROUP BY o.userId")
    List<Object[]> countOrdersByUser(@Param("activeStatuses") Collection<OrderStatus> activeStatuses);
}
