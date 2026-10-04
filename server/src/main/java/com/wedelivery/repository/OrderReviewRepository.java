package com.wedelivery.repository;

import com.wedelivery.entity.OrderReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderReviewRepository extends JpaRepository<OrderReview, Long> {
    Optional<OrderReview> findByOrderId(Long orderId);
    Optional<OrderReview> findByOrderNumber(String orderNumber);
    boolean existsByOrderId(Long orderId);
    boolean existsByOrderNumber(String orderNumber);
}
