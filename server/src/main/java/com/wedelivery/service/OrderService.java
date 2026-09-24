package com.wedelivery.service;

import com.wedelivery.dto.CheckoutRequest;
import com.wedelivery.dto.CheckoutResponse;
import com.wedelivery.entity.*;
import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.entity.enums.TrackingStage;
import com.wedelivery.entity.enums.VehicleStatus;
import com.wedelivery.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;
    private final StationRepository stationRepository;
    private final PaymentService paymentService;
    private final TrackingEventRepository trackingEventRepository;

    @Transactional(rollbackFor = Exception.class)
    public CheckoutResponse checkoutAndLockVehicle(CheckoutRequest req, User currentUser) {
        log.info("Processing checkout for user: {}, plan: {}", currentUser.getUsername(), req.getPlanType());

        // 1. 原子悲观锁锁定满足条件的可用载具
        List<Vehicle> availableVehicles = vehicleRepository.findAvailableVehiclesForLock(
                req.getStationId(),
                req.getVehicleType(),
                VehicleStatus.IDLE,
                new BigDecimal("20.00"), // 至少 20% 电量储备
                req.getPackageWeight(),
                req.getPackageVolume()
        );

        if (availableVehicles.isEmpty()) {
            throw new IllegalStateException("No available idle " + req.getVehicleType() + " at selected station. Please try another plan or retry later.");
        }

        Vehicle lockedVehicle = availableVehicles.get(0);
        log.info("Locked vehicle: {} (ID: {})", lockedVehicle.getVehicleCode(), lockedVehicle.getId());

        // 2. 生成外部可见的订单号
        String orderNumber = "SFORD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));

        LocalDateTime startTime = req.getScheduledStartTime() != null ? req.getScheduledStartTime() : LocalDateTime.now();
        LocalDateTime deliveryTime = req.getEstimatedDeliveryTime() != null ? req.getEstimatedDeliveryTime() : startTime.plusMinutes(30);

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .userId(currentUser.getId())
                .stationId(req.getStationId())
                .vehicleId(lockedVehicle.getId())
                .vehicleType(req.getVehicleType())
                .planType(req.getPlanType())
                .status(OrderStatus.PENDING_PAYMENT)
                .isStationPickup(Boolean.TRUE.equals(req.getIsStationPickup()))
                .pickupAddress(req.getPickupAddress())
                .pickupLat(req.getPickupLat())
                .pickupLng(req.getPickupLng())
                .dropoffAddress(req.getDropoffAddress())
                .dropoffLat(req.getDropoffLat())
                .dropoffLng(req.getDropoffLng())
                .packageWeight(req.getPackageWeight())
                .packageVolume(req.getPackageVolume())
                .totalDistance(req.getTotalDistance())
                .originPrice(req.getOriginPrice())
                .discountAmount(req.getDiscountAmount())
                .finalPrice(req.getFinalPrice())
                .scheduledStartTime(startTime)
                .estimatedDeliveryTime(deliveryTime)
                .build();

        order = orderRepository.save(order);

        // 3. 执行 Mock 扣款事务 (若失败会自动抛异常触发整个事务回滚)
        Payment payment = paymentService.processPayment(
                order.getId(),
                currentUser.getId(),
                req.getFinalPrice(),
                req.getCardNumber()
        );

        // 4. 扣款成功：将载具变更为 BUSY，订单变更为 PAID
        lockedVehicle.setStatus(VehicleStatus.BUSY);
        vehicleRepository.save(lockedVehicle);

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        // 5. 写入首个轨迹里程碑事件
        Station station = stationRepository.findById(req.getStationId()).orElse(null);
        BigDecimal initialLat = station != null ? station.getLatitude() : req.getPickupLat();
        BigDecimal initialLng = station != null ? station.getLongitude() : req.getPickupLng();

        TrackingEvent initialEvent = TrackingEvent.builder()
                .orderId(order.getId())
                .stage(TrackingStage.TO_PICKUP)
                .statusDescription("订单支付成功，已调度载具 " + lockedVehicle.getVehicleCode() + "，准备启航前往取件点。")
                .eventLat(initialLat)
                .eventLng(initialLng)
                .eventTime(LocalDateTime.now())
                .build();
        trackingEventRepository.save(initialEvent);

        return CheckoutResponse.builder()
                .orderNumber(order.getOrderNumber())
                .status(OrderStatus.PAID)
                .transactionNo(payment.getTransactionNo())
                .assignedVehicleCode(lockedVehicle.getVehicleCode())
                .estimatedDeliveryTime(order.getEstimatedDeliveryTime())
                .message("Order placed and vehicle locked successfully.")
                .build();
    }

    public Order getOrderByNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with number: " + orderNumber));
    }

    public List<Order> getUserOrders(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
