package com.wedelivery.service;

import com.wedelivery.dto.*;
import com.wedelivery.entity.*;
import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.entity.enums.PlanType;
import com.wedelivery.entity.enums.Role;
import com.wedelivery.entity.enums.TrackingStage;
import com.wedelivery.entity.enums.VehicleStatus;
import com.wedelivery.entity.enums.VehicleType;
import com.wedelivery.exception.NoVehicleAvailableException;
import com.wedelivery.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final RecommendationService recommendationService;

    /**
     * 核心契约下单接口：
     * 1. 动态重新估算方案（防篡改服务端计价）
     * 2. 原子悲观锁查车并锁定
     * 3. 生成持久化订单 (PENDING_PAYMENT)
     * 4. 执行 Mock 支付（若卡号尾号 0000 抛出异常触发整单回滚）
     * 5. 支付成功更新载具为 BUSY，订单为 PAID
     * 6. 记录首个轨迹里程碑事件 (TO_PICKUP)
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderCreateResponse createOrder(OrderCreateRequest req, User currentUser) {
        log.info("Processing createOrder contract for user: {}, candidate: {}",
                currentUser.getUsername(), req.getCandidateId());

        // 1. 提取并校验坐标与包裹规格
        RecommendationContractDto.LocationDto pickup = req.getPickup();
        RecommendationContractDto.LocationDto dropoff = req.getDropoff();
        RecommendationContractDto.PackageDto pkg = req.getEffectivePackage();

        BigDecimal pLat = (pickup != null && pickup.getLat() != null) ? pickup.getLat() : new BigDecimal("37.7858");
        BigDecimal pLng = (pickup != null && pickup.getLng() != null) ? pickup.getLng() : new BigDecimal("-122.4065");
        BigDecimal dLat = (dropoff != null && dropoff.getLat() != null) ? dropoff.getLat() : new BigDecimal("37.7596");
        BigDecimal dLng = (dropoff != null && dropoff.getLng() != null) ? dropoff.getLng() : new BigDecimal("-122.4269");

        BigDecimal weight = (pkg != null && pkg.getWeightKg() != null) ? pkg.getWeightKg() : new BigDecimal("1.50");
        BigDecimal volume = new BigDecimal("0.0200");
        if (pkg != null && pkg.getLengthCm() != null && pkg.getWidthCm() != null && pkg.getHeightCm() != null) {
            double l = pkg.getLengthCm().doubleValue() / 100.0;
            double w = pkg.getWidthCm().doubleValue() / 100.0;
            double h = pkg.getHeightCm().doubleValue() / 100.0;
            volume = BigDecimal.valueOf(l * w * h).setScale(4, RoundingMode.HALF_UP);
        }

        // 2. 构造 QuoteRequest 并调用 RecommendationService 实时重算方案
        QuoteRequest quoteReq = QuoteRequest.builder()
                .pickupAddress((pickup != null && pickup.getLine1() != null) ? pickup.getLine1() : "Market St, San Francisco")
                .pickupLat(pLat)
                .pickupLng(pLng)
                .dropoffAddress((dropoff != null && dropoff.getLine1() != null) ? dropoff.getLine1() : "Mission St, San Francisco")
                .dropoffLat(dLat)
                .dropoffLng(dLng)
                .packageWeight(weight)
                .packageVolume(volume)
                .build();

        QuoteResponse quoteResponse = recommendationService.generateRecommendations(quoteReq, currentUser);
        List<PlanOptionDto> availablePlans = quoteResponse.getPlans();
        if (availablePlans == null || availablePlans.isEmpty()) {
            throw new NoVehicleAvailableException("No delivery options available for this route or package specification.");
        }

        // 3. 根据 candidateId 匹配目标方案类型
        String candId = req.getCandidateId().toUpperCase();
        PlanType targetPlanType;
        if (candId.contains("OFF_PEAK") || candId.contains("ECO")) {
            targetPlanType = PlanType.OFF_PEAK;
        } else if (candId.contains("FASTEST") || candId.contains("DRONE")) {
            targetPlanType = PlanType.FASTEST;
        } else {
            targetPlanType = PlanType.BEST_VALUE;
        }

        PlanOptionDto matchedPlan = availablePlans.stream()
                .filter(p -> p.getPlanType() == targetPlanType)
                .findFirst()
                .orElse(availablePlans.get(0));

        log.info("Matched plan: {} for station: {} (ID: {})",
                matchedPlan.getPlanType(), matchedPlan.getStationName(), matchedPlan.getStationId());

        // 4. 原子悲观锁查车并锁定首辆可用载具
        List<Vehicle> availableVehicles = vehicleRepository.findAvailableVehiclesForLock(
                matchedPlan.getStationId(),
                matchedPlan.getVehicleType(),
                VehicleStatus.IDLE,
                new BigDecimal("15.00"), // 基础安全电量储备
                weight,
                volume
        );

        if (availableVehicles.isEmpty()) {
            throw new NoVehicleAvailableException("No available idle " + matchedPlan.getVehicleType()
                    + " at station " + matchedPlan.getStationName() + ". Please retry or select another plan.");
        }

        Vehicle lockedVehicle = availableVehicles.get(0);
        log.info("Locked vehicle: {} (ID: {})", lockedVehicle.getVehicleCode(), lockedVehicle.getId());

        // 5. 生成系统唯一订单编号
        String orderNumber = "SFORD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .userId(currentUser.getId())
                .stationId(matchedPlan.getStationId())
                .vehicleId(lockedVehicle.getId())
                .vehicleType(matchedPlan.getVehicleType())
                .planType(matchedPlan.getPlanType())
                .status(OrderStatus.PENDING_PAYMENT)
                .isStationPickup(false)
                .pickupAddress(quoteReq.getPickupAddress())
                .pickupLat(pLat)
                .pickupLng(pLng)
                .dropoffAddress(quoteReq.getDropoffAddress())
                .dropoffLat(dLat)
                .dropoffLng(dLng)
                .packageWeight(weight)
                .packageVolume(volume)
                .totalDistance(matchedPlan.getTotalDistance())
                .originPrice(matchedPlan.getOriginPrice())
                .discountAmount(matchedPlan.getDiscountAmount())
                .finalPrice(matchedPlan.getFinalPrice())
                .scheduledStartTime(matchedPlan.getScheduledStartTime())
                .estimatedDeliveryTime(matchedPlan.getEstimatedDeliveryTime())
                .build();

        order = orderRepository.save(order);

        // 6. 执行 Mock 扣款事务 (若卡号/paymentMethodId 尾号以 0000 结尾将抛出 PaymentDeclinedException 触发回滚)
        String cardIdentifier = req.getPaymentMethodId() != null && !req.getPaymentMethodId().isBlank()
                ? req.getPaymentMethodId()
                : (req.getCardNumber() != null ? req.getCardNumber() : "4532 8901 2345 6789");

        Payment payment = paymentService.processPayment(
                order.getId(),
                currentUser.getId(),
                order.getFinalPrice(),
                cardIdentifier
        );

        // 7. 扣款成功：将载具变更为 BUSY，订单变更为 PAID
        lockedVehicle.setStatus(VehicleStatus.BUSY);
        vehicleRepository.save(lockedVehicle);

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        // 8. 写入首个轨迹里程碑事件
        Station station = stationRepository.findById(matchedPlan.getStationId()).orElse(null);
        BigDecimal initialLat = station != null ? station.getLatitude() : pLat;
        BigDecimal initialLng = station != null ? station.getLongitude() : pLng;

        TrackingEvent initialEvent = TrackingEvent.builder()
                .orderId(order.getId())
                .stage(TrackingStage.TO_PICKUP)
                .statusDescription("订单支付成功，已调度载具 " + lockedVehicle.getVehicleCode() + "，准备启航前往取件点。")
                .eventLat(initialLat)
                .eventLng(initialLng)
                .eventTime(LocalDateTime.now())
                .build();
        trackingEventRepository.save(initialEvent);

        return OrderCreateResponse.builder()
                .orderId(order.getOrderNumber())
                .status("PENDING")
                .estimatedTimeMinutes(matchedPlan.getEstimatedMinutes())
                .estimatedCost(order.getFinalPrice())
                .transactionNo(payment.getTransactionNo())
                .assignedVehicleCode(lockedVehicle.getVehicleCode())
                .message("Order created and vehicle locked successfully.")
                .build();
    }

    /**
     * 确认签收业务闭环：
     * 1. 校验用户身份
     * 2. 更新订单状态为 DELIVERED 并记录实际送达时间
     * 3. 将对应车辆从 BUSY 释放回 IDLE 状态
     * 4. 写入签收里程碑事件
     */
    @Transactional(rollbackFor = Exception.class)
    public Order confirmReceipt(String orderNumber, User currentUser) {
        Order order = getOrderByNumber(orderNumber);

        if (currentUser != null && currentUser.getRole() != Role.ADMIN && !order.getUserId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("You are not authorized to confirm receipt for this order.");
        }

        if (order.getStatus() == OrderStatus.DELIVERED) {
            return order;
        }

        order.setStatus(OrderStatus.DELIVERED);
        order.setActualDeliveryTime(LocalDateTime.now());
        orderRepository.save(order);

        // 释放载具回到 IDLE 状态
        if (order.getVehicleId() != null) {
            vehicleRepository.findById(order.getVehicleId()).ifPresent(v -> {
                if (v.getStatus() == VehicleStatus.BUSY) {
                    v.setStatus(VehicleStatus.IDLE);
                    vehicleRepository.save(v);
                    log.info("Vehicle {} successfully released back to IDLE upon order {} delivery.",
                            v.getVehicleCode(), order.getOrderNumber());
                }
            });
        }

        // 写入签收完成轨迹事件
        TrackingEvent event = TrackingEvent.builder()
                .orderId(order.getId())
                .stage(TrackingStage.COMPLETED)
                .statusDescription("收件人已确认签收，订单履约圆满完成，载具已停泊就位。")
                .eventLat(order.getDropoffLat())
                .eventLng(order.getDropoffLng())
                .eventTime(LocalDateTime.now())
                .build();
        trackingEventRepository.save(event);

        return order;
    }

    /**
     * 向下兼容的原有结账接口
     */
    @Transactional(rollbackFor = Exception.class)
    public CheckoutResponse checkoutAndLockVehicle(CheckoutRequest req, User currentUser) {
        log.info("Processing checkout for user: {}, plan: {}", currentUser.getUsername(), req.getPlanType());

        List<Vehicle> availableVehicles = vehicleRepository.findAvailableVehiclesForLock(
                req.getStationId(),
                req.getVehicleType(),
                VehicleStatus.IDLE,
                new BigDecimal("20.00"),
                req.getPackageWeight(),
                req.getPackageVolume()
        );

        if (availableVehicles.isEmpty()) {
            throw new NoVehicleAvailableException("No available idle " + req.getVehicleType() + " at selected station. Please try another plan or retry later.");
        }

        Vehicle lockedVehicle = availableVehicles.get(0);

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

        Payment payment = paymentService.processPayment(
                order.getId(),
                currentUser.getId(),
                req.getFinalPrice(),
                req.getCardNumber()
        );

        lockedVehicle.setStatus(VehicleStatus.BUSY);
        vehicleRepository.save(lockedVehicle);

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

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
