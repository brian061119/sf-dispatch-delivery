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
     * Core contract order creation:
     * 1. Dynamically recalculate plan (server-side price validation to prevent tampering)
     * 2. Atomically lock vehicle via pessimistic lock
     * 3. Persist order entity (PENDING_PAYMENT)
     * 4. Process mock payment (triggers rollback if card ends with 0000)
     * 5. Transition vehicle to BUSY and order to PAID upon payment success
     * 6. Record initial tracking milestone event (TO_PICKUP)
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderCreateResponse createOrder(OrderCreateRequest req, User currentUser) {
        log.info("Processing createOrder contract for user: {}, candidate: {}",
                currentUser.getUsername(), req.getCandidateId());

        // 1. Extract and validate coordinates and package specifications
        RecommendationContractDto.LocationDto pickup = req.getPickup();
        RecommendationContractDto.LocationDto dropoff = req.getDropoff();
        RecommendationContractDto.PackageDto pkg = req.getEffectivePackage();

        if (pickup == null || pickup.getLat() == null || pickup.getLng() == null) {
            log.warn("⚠️ Pickup coordinates missing from request, falling back to Downtown SF default for demo resilience.");
        }
        if (dropoff == null || dropoff.getLat() == null || dropoff.getLng() == null) {
            log.warn("⚠️ Dropoff coordinates missing from request, falling back to Mission SF default for demo resilience.");
        }

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

        // 2. Build QuoteRequest and invoke RecommendationService to recalculate plan
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

        // 3. Match target plan type according to candidateId
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

        // 4. Atomically query and lock first available vehicle using pessimistic lock
        List<Vehicle> availableVehicles = vehicleRepository.findAvailableVehiclesForLock(
                matchedPlan.getStationId(),
                matchedPlan.getVehicleType(),
                VehicleStatus.IDLE,
                new BigDecimal("15.00"), // Minimum battery reserve
                weight,
                volume
        );

        if (availableVehicles.isEmpty()) {
            throw new NoVehicleAvailableException("No available idle " + matchedPlan.getVehicleType()
                    + " at station " + matchedPlan.getStationName() + ". Please retry or select another plan.");
        }

        Vehicle lockedVehicle = availableVehicles.get(0);
        log.info("Locked vehicle: {} (ID: {})", lockedVehicle.getVehicleCode(), lockedVehicle.getId());

        // 5. Generate unique system order number
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

        // 6. Strictly extract and validate payment credential
        String cardIdentifier = req.getPaymentMethodId() != null && !req.getPaymentMethodId().isBlank()
                ? req.getPaymentMethodId()
                : req.getCardNumber();

        if (cardIdentifier == null || cardIdentifier.isBlank()) {
            throw new IllegalArgumentException("Payment method or card number is required to complete checkout.");
        }

        Payment payment = paymentService.processPayment(
                order.getId(),
                currentUser.getId(),
                order.getFinalPrice(),
                cardIdentifier
        );

        // 7. Payment succeeded: update vehicle to BUSY and order to PAID
        lockedVehicle.setStatus(VehicleStatus.BUSY);
        vehicleRepository.save(lockedVehicle);

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        // 8. Record initial tracking milestone event
        Station station = stationRepository.findById(matchedPlan.getStationId()).orElse(null);
        BigDecimal initialLat = station != null ? station.getLatitude() : pLat;
        BigDecimal initialLng = station != null ? station.getLongitude() : pLng;

        TrackingEvent initialEvent = TrackingEvent.builder()
                .orderId(order.getId())
                .stage(TrackingStage.TO_PICKUP)
                .statusDescription("Payment succeeded. Dispatched " + lockedVehicle.getVehicleCode() + ", en route to pickup location.")
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
     * Delivery confirmation business closure:
     * 1. Verify user authorization
     * 2. Update order status to DELIVERED and record actual delivery time
     * 3. Release assigned vehicle back to IDLE
     * 4. Record delivery completion tracking milestone
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

        // Release vehicle back to IDLE state
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

        // Record delivery completion milestone event
        TrackingEvent event = TrackingEvent.builder()
                .orderId(order.getId())
                .stage(TrackingStage.COMPLETED)
                .statusDescription("Delivery confirmed by recipient. Order fulfilled successfully, vehicle returned and docked.")
                .eventLat(order.getDropoffLat())
                .eventLng(order.getDropoffLng())
                .eventTime(LocalDateTime.now())
                .build();
        trackingEventRepository.save(event);

        return order;
    }


    public Order getOrderByNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with number: " + orderNumber));
    }

    public List<Order> getUserOrders(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
