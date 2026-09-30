package com.wedelivery.service;

import com.wedelivery.dto.*;
import com.wedelivery.entity.*;
import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.entity.enums.PlanType;
import com.wedelivery.entity.enums.Role;
import com.wedelivery.entity.enums.TrackingStage;
import com.wedelivery.entity.enums.VehicleStatus;
import com.wedelivery.exception.NoVehicleAvailableException;
import com.wedelivery.exception.ResourceNotFoundException;
import com.wedelivery.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
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

    // Tracking code alphabet: excludes ambiguous 0/O, 1/I (32 characters; 16 chars ≈ 80 bits entropy)
    private static final String TRACKING_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TRACKING_CODE_LENGTH = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Core contract order creation:
     * 1. Dynamically recalculate plan (server-side price validation to prevent tampering)
     * 2. Atomically lock vehicle via pessimistic lock
     * 3. Persist order entity (PENDING_PAYMENT)
     * 4. Process mock payment (triggers rollback if card ends with 0000)
     * 5. Transition vehicle to IN_DELIVERY and update real-time telemetry upon payment success
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
            throw new IllegalArgumentException("Pickup coordinates (lat, lng) are required.");
        }
        if (dropoff == null || dropoff.getLat() == null || dropoff.getLng() == null) {
            throw new IllegalArgumentException("Dropoff coordinates (lat, lng) are required.");
        }

        BigDecimal pLat = pickup.getLat();
        BigDecimal pLng = pickup.getLng();
        BigDecimal dLat = dropoff.getLat();
        BigDecimal dLng = dropoff.getLng();

        if (!RecommendationService.isWithinSanFrancisco(pLat, pLng) || !RecommendationService.isWithinSanFrancisco(dLat, dLng)) {
            throw new IllegalArgumentException("Delivery address is outside the San Francisco service area.");
        }

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
        String candId = req.getCandidateId() != null ? req.getCandidateId().trim() : "CAND-BEST_VALUE";
        PlanOptionDto matchedPlan = availablePlans.stream()
                .filter(p -> ("CAND-" + p.getPlanType().name()).equalsIgnoreCase(candId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Selected plan " + candId + " is no longer available. Please refresh the recommendations."));

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

        // 5. Generate unique system order number and secure tracking code
        String orderNumber = "SFORD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        String trackingCode = generateTrackingCode();

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .trackingCode(trackingCode)
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

        // 6. Extract payment credential (supports mock fallback for testing)
        String cardIdentifier = req.getPaymentMethodId() != null && !req.getPaymentMethodId().isBlank()
                ? req.getPaymentMethodId()
                : req.getCardNumber();

        if (cardIdentifier == null || cardIdentifier.isBlank()) {
            cardIdentifier = "pm_mock_card";
        }

        Payment payment = paymentService.processPayment(
                order.getId(),
                currentUser.getId(),
                order.getFinalPrice(),
                cardIdentifier
        );

        // 7. Payment succeeded: update vehicle to IN_DELIVERY and synchronize realtime machine state
        Station station = stationRepository.findById(matchedPlan.getStationId()).orElse(null);
        LocalDateTime now = LocalDateTime.now();

        lockedVehicle.setStatus(VehicleStatus.IN_DELIVERY);
        lockedVehicle.setStatusUpdatedAt(now);
        lockedVehicle.setLocationCode(Vehicle.LOCATION_NOT_AT_STATION);
        lockedVehicle.setCurrentSpeed(lockedVehicle.getCruiseSpeed());
        lockedVehicle.setSpeedUpdatedAt(now);
        lockedVehicle.setCurrentLat(station != null ? station.getLatitude() : pLat);
        lockedVehicle.setCurrentLng(station != null ? station.getLongitude() : pLng);
        lockedVehicle.setPositionUpdatedAt(now);
        vehicleRepository.save(lockedVehicle);

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        // 8. Record initial tracking milestone event
        BigDecimal initialLat = station != null ? station.getLatitude() : pLat;
        BigDecimal initialLng = station != null ? station.getLongitude() : pLng;

        TrackingEvent initialEvent = TrackingEvent.builder()
                .orderId(order.getId())
                .stage(TrackingStage.TO_PICKUP)
                .statusDescription("Payment succeeded. Dispatched " + lockedVehicle.getVehicleCode() + ", en route to pickup location.")
                .eventLat(initialLat)
                .eventLng(initialLng)
                .eventTime(now)
                .build();
        trackingEventRepository.save(initialEvent);

        return OrderCreateResponse.builder()
                .orderId(order.getOrderNumber())
                .trackingCode(order.getTrackingCode())
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
     * 1. Verify user authorization (only customer who placed order or ADMIN)
     * 2. Update order status to DELIVERED and record actual delivery time
     * 3. Release assigned vehicle back to IDLE
     * 4. Record delivery completion tracking milestone
     */
    @Transactional(rollbackFor = Exception.class)
    public Order confirmReceipt(String orderNumber, User currentUser) {
        Order order = getOrderByNumber(orderNumber);

        if (currentUser != null && currentUser.getRole() != Role.ADMIN && !order.getUserId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Only the customer who placed order " + orderNumber + " can confirm receipt");
        }

        if (order.getStatus() == OrderStatus.DELIVERED) {
            return order;
        }

        order.setStatus(OrderStatus.DELIVERED);
        if (order.getActualDeliveryTime() == null) {
            order.setActualDeliveryTime(LocalDateTime.now());
        }
        orderRepository.save(order);

        // Release vehicle back to IDLE state
        if (order.getVehicleId() != null) {
            vehicleRepository.findById(order.getVehicleId()).ifPresent(v -> {
                if (v.getStatus() == VehicleStatus.IN_DELIVERY) {
                    LocalDateTime now = LocalDateTime.now();
                    v.setStatus(VehicleStatus.IDLE);
                    v.setStatusUpdatedAt(now);
                    v.setCurrentSpeed(BigDecimal.ZERO);
                    v.setSpeedUpdatedAt(now);
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
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));
    }

    /**
     * Query order and verify access permission: only order owner or admin can view.
     * Throws AccessDeniedException if not authorized (mapped to 403 by GlobalExceptionHandler).
     */
    public Order getAccessibleOrder(String orderNumber, User currentUser) {
        Order order = getOrderByNumber(orderNumber);
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to access order.");
        }
        boolean isOwner = order.getUserId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You do not have access to order " + orderNumber);
        }
        return order;
    }

    public List<Order> getUserOrders(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private String generateTrackingCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(TRACKING_CODE_LENGTH);
            for (int i = 0; i < TRACKING_CODE_LENGTH; i++) {
                sb.append(TRACKING_CODE_ALPHABET.charAt(SECURE_RANDOM.nextInt(TRACKING_CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while (orderRepository.existsByTrackingCode(code));
        return code;
    }
}
