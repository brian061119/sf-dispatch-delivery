package com.wedelivery.service;

import com.wedelivery.dto.*;
import com.wedelivery.entity.*;
import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.entity.enums.PaymentStatus;
import com.wedelivery.entity.enums.PlanType;
import com.wedelivery.entity.enums.Role;
import com.wedelivery.entity.enums.TrackingStage;
import com.wedelivery.entity.enums.VehicleStatus;
import com.wedelivery.entity.enums.VehicleType;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;
    private final StationRepository stationRepository;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final TrackingEventRepository trackingEventRepository;
    private final RecommendationService recommendationService;
    private final RouteService routeService;
    private final OrderReviewRepository orderReviewRepository;
    private final UserRepository userRepository;

    // Tracking code alphabet: excludes ambiguous 0/O, 1/I (32 characters; 16 chars ≈ 80 bits entropy)
    private static final String TRACKING_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TRACKING_CODE_LENGTH = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 下单后的最短可取消 / 可改单宽限窗口（秒）。
     *
     * <p>为什么需要一个时间下限：顾客自投（取件点就是站点，见 isStationPickup）或
     * 取件点恰好订在站点坐标上时，「站→取件」航段为 0，按比例算出的取件时刻就是
     * 派单那一刻。若直接据此判定"已取件"，结果是<b>下单成功即不可取消</b>——这在
     * 任何真实产品里都不存在：
     * <ul>
     *   <li>Uber / Lyft / Waymo：派单后仍有免费取消窗口（Uber 约 2 分钟），且始终可取消到司机(车)到达为止；</li>
     *   <li>Amazon：发货前可整单取消，并在下单后保留一段"后悔"窗口；</li>
     *   <li>顺丰 / WeDelivery 式站点自寄：包裹被揽收前均可撤单，系统会给一个固定期限。</li>
     * </ul>
     * 因此这里统一取「按比例推算的取件时刻」与「下单后 grace 秒」中较晚的一个：
     * 正常上门取件的订单不受影响（那一班车开到取件点通常远大于 2 分钟），
     * 只有首段为 0 的订单才退化到这个窗口。
     */
    private static final long PICKUP_CANCELLATION_GRACE_SECONDS = 120;

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
        BigDecimal requiredBattery = recommendationService.calculateRequiredInitialBattery(
                matchedPlan.getTotalDistance(),
                matchedPlan.getVehicleType(),
                weight
        );

        boolean isVip = currentUser != null && currentUser.isVip();
        BigDecimal capacityDivisor = isVip ? new BigDecimal("1.10") : BigDecimal.ONE;
        BigDecimal checkWeight = weight.divide(capacityDivisor, 4, RoundingMode.HALF_UP);
        BigDecimal checkVolume = volume.divide(capacityDivisor, 4, RoundingMode.HALF_UP);

        List<Vehicle> availableVehicles = vehicleRepository.findAvailableVehiclesForLock(
                matchedPlan.getStationId(),
                matchedPlan.getVehicleType(),
                RecommendationService.DISPATCHABLE_STATUSES,
                requiredBattery,
                checkWeight,
                checkVolume
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

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot confirm receipt for a cancelled order.");
        }

        // Receipt can only be confirmed once the package has reached the drop-off point.
        // 用秒数判定而不是写死的比例：estimatedDeliveryTime 现在表示「包裹送达时刻」，
        // 送达窗口走完才允许签收（旧的 0.75 会让人在包裹还在路上时就签收）。
        TripTimeline timeline = tripTimeline(order);
        long elapsedSeconds = elapsedTripSeconds(order);
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT
                || timeline == null || elapsedSeconds < timeline.deliverySeconds) {
            throw new IllegalStateException("The package has not been delivered yet. Receipt can only be confirmed after delivery.");
        }

        order.setStatus(OrderStatus.DELIVERED);
        if (order.getActualDeliveryTime() == null) {
            order.setActualDeliveryTime(LocalDateTime.now());
        }
        orderRepository.save(order);

        // Release the vehicle back to its resting state (charge at station if not full, otherwise idle),
        // but only once it is docked and still ours. A vehicle that is still driving back is left in
        // IN_DELIVERY for the simulator to bring home; one already reassigned to a newer order is not touched.
        boolean vehicleDocked = timeline != null && elapsedSeconds >= timeline.loopSeconds;
        boolean vehicleReassigned = order.getVehicleId() != null
                && orderRepository.existsByVehicleIdAndIdGreaterThanAndStatusNot(
                        order.getVehicleId(), order.getId(), OrderStatus.CANCELLED);
        if (order.getVehicleId() != null && vehicleDocked && !vehicleReassigned) {
            vehicleRepository.findById(order.getVehicleId()).ifPresent(v -> {
                if (v.getStatus() == VehicleStatus.IN_DELIVERY) {
                    LocalDateTime now = LocalDateTime.now();
                    v.setStatus(v.restingStatus());
                    v.setStatusUpdatedAt(now);
                    v.setCurrentSpeed(BigDecimal.ZERO);
                    v.setSpeedUpdatedAt(now);
                    vehicleRepository.save(v);
                    log.info("Vehicle {} released back to {} upon order {} delivery.",
                            v.getVehicleCode(), v.getStatus(), order.getOrderNumber());
                }
            });
        }

        // Record delivery completion milestone event
        TrackingEvent event = TrackingEvent.builder()
                .orderId(order.getId())
                .stage(TrackingStage.DELIVERY_CONFIRMED)
                .statusDescription("Delivery confirmed by recipient. Order fulfilled successfully, vehicle returned and docked.")
                .eventLat(order.getDropoffLat())
                .eventLng(order.getDropoffLng())
                .eventTime(LocalDateTime.now())
                .build();
        trackingEventRepository.save(event);

        return order;
    }

    private static final BigDecimal DISPATCH_SERVICE_FEE = new BigDecimal("2.50");

    /**
     * Elapsed seconds of the scheduled trip, 0 when the schedule is missing or has not started yet.
     */
    private long elapsedTripSeconds(Order order) {
        if (order.getScheduledStartTime() == null) {
            return 0L;
        }
        return Math.max(0, Duration.between(order.getScheduledStartTime(), LocalDateTime.now()).getSeconds());
    }

    /** 一单配送的时间线：自 scheduledStartTime 起算，到达各里程碑所需的秒数。 */
    private static final class TripTimeline {
        final long pickupSeconds;
        final long deliverySeconds;
        final long loopSeconds;

        TripTimeline(long pickupSeconds, long deliverySeconds, long loopSeconds) {
            this.pickupSeconds = pickupSeconds;
            this.deliverySeconds = deliverySeconds;
            this.loopSeconds = loopSeconds;
        }
    }

    /**
     * 配送闭环的时间线：按「站→取件→送达→返站」的里程等比，把秒数分配到三个里程碑。
     *
     * <p>可否取消/改单、可否签收、可否释放车辆这三处判断<b>必须共用这一份算法</b>——各自写一份
     * 比例常量正是之前语义漂移的来源：<code>estimatedDeliveryTime</code> 的语义从「车回站时刻」
     * 改为「包裹送达时刻」之后，任何写死的 0.25 / 0.75 / 1.0 都会指向错误的时间点。
     *
     * <ul>
     *   <li>{@code pickupSeconds} — 包裹上车，之后不可取消/改单</li>
     *   <li>{@code deliverySeconds} — 包裹送到客户手上，之后才允许签收</li>
     *   <li>{@code loopSeconds} — 车辆回到站点，之后才允许把车放回待命/充电</li>
     * </ul>
     *
     * @return null when the order has no schedule or its station is gone (caller treats it as "nothing happened yet")
     */
    private TripTimeline tripTimeline(Order order) {
        if (order.getScheduledStartTime() == null || order.getEstimatedDeliveryTime() == null
                || order.getStationId() == null) {
            return null;
        }
        Station station = stationRepository.findById(order.getStationId()).orElse(null);
        if (station == null) {
            return null;
        }
        BigDecimal sLat = station.getLatitude();
        BigDecimal sLng = station.getLongitude();
        BigDecimal pLat = Boolean.TRUE.equals(order.getIsStationPickup()) ? sLat : order.getPickupLat();
        BigDecimal pLng = Boolean.TRUE.equals(order.getIsStationPickup()) ? sLng : order.getPickupLng();
        double[] milestones = routeService.milestoneFractions(
                sLat, sLng, pLat, pLng, order.getDropoffLat(), order.getDropoffLng(), order.getVehicleType());
        long deliverySeconds = Math.max(60, Duration.between(
                order.getScheduledStartTime(), order.getEstimatedDeliveryTime()).getSeconds());
        long loopSeconds = Math.max(deliverySeconds, Math.round(deliverySeconds / milestones[1]));
        return new TripTimeline(Math.round(loopSeconds * milestones[0]), deliverySeconds, loopSeconds);
    }

    /**
     * Cancel order business closure:
     * 1. Verify user authorization (only customer who placed order or ADMIN)
     * 2. Validate current order status:
     *    - If already CANCELLED: return immediately (idempotent)
     *    - If DELIVERED: throw IllegalStateException (cannot cancel delivered order)
     *    - If package already picked up (IN_TRANSIT or past the pickup milestone on the tracking timeline): throw IllegalStateException (cannot cancel after pickup!)
     * 3. Apply refund & dispatch fee policy:
     *    - Before dispatch / waiting for vehicle (PAID, PENDING_PAYMENT): 100% full refund, $0 fee
     *    - En route to pickup (PICKING_UP or dispatched): deduct dispatch service fee ($2.50) and refund remainder
     * 4. Vehicle return strategy:
     *    - If vehicle is en route to pickup, reroute to nearest station with available bays (targetStationId)
     *    - If vehicle has not departed, release directly back to IDLE
     * 5. Record cancellation tracking milestone and refund payment
     */
    @Transactional(rollbackFor = Exception.class)
    public Order cancelOrder(String orderNumber, User currentUser) {
        Order order = getOrderByNumber(orderNumber);

        if (currentUser != null && currentUser.getRole() != Role.ADMIN && !order.getUserId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Only the customer who placed order " + orderNumber + " can cancel it");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return order;
        }

        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalStateException("Cannot cancel an order that has already been delivered.");
        }

        // Core validation: Cannot cancel order if package has already been picked up (in transit)
        boolean packagePickedUp = packagePickedUpByTimeline(order);

        if (packagePickedUp) {
            throw new IllegalStateException("The package has already been picked up and is in transit. Cancellation is not allowed.");
        }

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);

        // 1. Graduated cancellation and refund policy:
        // Cancellation is permitted before pickup:
        // - En route to pickup (previousStatus == PICKING_UP): deduct dispatch service fee ($2.50) and refund remainder
        // - Not yet departed (PENDING_PAYMENT, PAID): 100% full refund with $0 cancellation fee
        BigDecimal finalPrice = order.getFinalPrice() != null ? order.getFinalPrice() : BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal refund = finalPrice;

        boolean vehicleEnRouteToPickup = (previousStatus == OrderStatus.PICKING_UP);
        boolean isVip = (currentUser != null && currentUser.isVip())
                || userRepository.findById(order.getUserId()).map(User::isVip).orElse(false);

        if (vehicleEnRouteToPickup) {
            if (isVip) {
                fee = BigDecimal.ZERO;
                refund = finalPrice;
                log.info("VIP privilege: dispatch service fee waived for order {}", order.getOrderNumber());
            } else if (finalPrice.compareTo(DISPATCH_SERVICE_FEE) > 0) {
                fee = DISPATCH_SERVICE_FEE;
                refund = finalPrice.subtract(DISPATCH_SERVICE_FEE);
            } else {
                fee = finalPrice;
                refund = BigDecimal.ZERO;
            }
        }
        order.setCancellationFee(fee);
        order.setRefundAmount(refund);

        // 2. Vehicle disposition and dynamic rerouting:
        Station returnStation = null;
        if (order.getVehicleId() != null) {
            Vehicle vehicle = vehicleRepository.findById(order.getVehicleId()).orElse(null);
            if (vehicle != null) {
                LocalDateTime now = LocalDateTime.now();
                if (vehicleEnRouteToPickup) {
                    BigDecimal curLat = vehicle.getCurrentLat() != null ? vehicle.getCurrentLat() : order.getPickupLat();
                    BigDecimal curLng = vehicle.getCurrentLng() != null ? vehicle.getCurrentLng() : order.getPickupLng();

                    returnStation = findNearestStationWithBays(vehicle, curLat, curLng, order.getStationId());
                    if (returnStation != null) {
                        order.setReturnStationId(returnStation.getId());
                        vehicle.setTargetStationId(returnStation.getId());
                    }
                    log.info("Vehicle {} cancelled while heading to pickup. Rerouting to nearest available station {} (ID: {})",
                            vehicle.getVehicleCode(), returnStation != null ? returnStation.getName() : "N/A",
                            returnStation != null ? returnStation.getId() : order.getStationId());
                } else {
                    vehicle.setStatus(VehicleStatus.IDLE);
                    vehicle.setStatusUpdatedAt(now);
                    vehicle.setCurrentSpeed(BigDecimal.ZERO);
                    vehicle.setSpeedUpdatedAt(now);
                    vehicle.setTargetStationId(null);
                    log.info("Vehicle {} released directly back to IDLE upon pre-dispatch cancellation.", vehicle.getVehicleCode());
                }
                vehicleRepository.save(vehicle);
            }
        }

        orderRepository.save(order);

        // 3. Process payment refund
        final BigDecimal finalRefund = refund;
        final BigDecimal finalFee = fee;
        paymentRepository.findByOrderId(order.getId()).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                payment.setStatus(PaymentStatus.REFUNDED);
                paymentRepository.save(payment);
                log.info("Refund processed for order {}: amount ${} (cancellation fee: ${})",
                        order.getOrderNumber(), finalRefund, finalFee);
            }
        });

        // 4. Milestone tracking event

        String stationDesc = returnStation != null
                ? " Rerouting to nearest available Station #" + returnStation.getId() + " (" + returnStation.getName() + ")."
                : "";
        String feeDesc;
        if (isVip && vehicleEnRouteToPickup) {
            feeDesc = " VIP privilege: Dispatch service fee waived ($0.00). Full refund: $" + refund + ".";
        } else if (fee.compareTo(BigDecimal.ZERO) > 0) {
            feeDesc = " Dispatch service fee: $" + fee + ", refunded: $" + refund + ".";
        } else {
            feeDesc = " Full refund: $" + refund + ".";
        }

        TrackingEvent event = TrackingEvent.builder()
                .orderId(order.getId())
                .stage(TrackingStage.CANCELLED)
                .statusDescription("Order cancelled." + feeDesc + stationDesc)
                .eventLat(order.getPickupLat())
                .eventLng(order.getPickupLng())
                .eventTime(LocalDateTime.now())
                .build();
        trackingEventRepository.save(event);

        return order;
    }

    /**
     * Finds the nearest station with available bays for the returning vehicle.
     * Falls back to origin station if all stations are fully occupied.
     */
    private Station findNearestStationWithBays(Vehicle vehicle, BigDecimal curLat, BigDecimal curLng, Long originStationId) {
        List<Station> stations = stationRepository.findAll();
        List<Vehicle> allVehicles = vehicleRepository.findAll();

        Station originStation = stations.stream()
                .filter(s -> s.getId().equals(originStationId))
                .findFirst()
                .orElse(null);

        if (stations.isEmpty()) {
            return originStation;
        }

        double cLat = curLat != null ? curLat.doubleValue() : 37.7749;
        double cLng = curLng != null ? curLng.doubleValue() : -122.4194;

        List<Station> candidateStations = stations.stream().filter(station -> {
            int totalBays = vehicle.getVehicleType() == VehicleType.DRONE
                    ? station.getTotalDroneBays()
                    : station.getTotalRobotBays();

            long occupiedBays = allVehicles.stream().filter(v -> {
                if (v.getId().equals(vehicle.getId())) return false;
                if (v.getVehicleType() != vehicle.getVehicleType()) return false;
                boolean isTarget = station.getId().equals(v.getTargetStationId());
                boolean isAtStation = v.getLocationCode() != null && v.getLocationCode() == station.getId().intValue();
                return isTarget || isAtStation;
            }).count();

            return (totalBays - occupiedBays) > 0;
        }).collect(Collectors.toList());

        if (candidateStations.isEmpty()) {
            log.warn("No stations with available bays found for vehicle {}. Returning to origin station {}.",
                    vehicle.getVehicleCode(), originStationId);
            return originStation != null ? originStation : stations.get(0);
        }

        return candidateStations.stream()
                .min(Comparator.comparingDouble(s ->
                        routeService.calculateStraightDistance(
                                s.getLatitude().doubleValue(), s.getLongitude().doubleValue(),
                                cLat, cLng)))
                .orElse(originStation != null ? originStation : candidateStations.get(0));
    }

    /**
     * Customer order modification (only 1 modification permitted before package pickup):
     * 1. Strictly enforces 1-modification limit (modifiedCount == 1, hasBeenModified = true)
     * 2. Supports modifying delivery method (e.g. drone upgrade), package specs (weight/dimensions), and addresses
     * 3. If pickup location, package specs, station, or vehicle type changes:
     *    - Invokes RecommendationService to re-evaluate optimal station and delivery plan
     *    - Reallocates vehicle from the new optimal station and releases previous vehicle back to IDLE
     *    - Settles price difference (surcharge / partial refund)
     */
    @Transactional(rollbackFor = Exception.class)
    public Order updateOrder(String orderNumber, OrderUpdateRequest request, User currentUser) {
        Order order = getOrderByNumber(orderNumber);

        if (currentUser != null && currentUser.getRole() != Role.ADMIN && !order.getUserId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Only the customer who placed order " + orderNumber + " can modify it");
        }

        // Core validation: Cannot modify order after package has already been picked up (in transit)
        boolean packagePickedUp = packagePickedUpByTimeline(order);

        if (packagePickedUp) {
            throw new IllegalStateException("Package has already been picked up and is in transit. Order can only be modified before pickup starts.");
        }

        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot modify an order that is " + order.getStatus());
        }

        boolean isVip = (currentUser != null && currentUser.isVip())
                || userRepository.findById(order.getUserId()).map(User::isVip).orElse(false);
        int maxModifications = isVip ? 2 : 1;
        int currentCount = order.getModifiedCount() != null ? order.getModifiedCount() : (Boolean.TRUE.equals(order.getHasBeenModified()) ? 1 : 0);
        if (currentCount >= maxModifications) {
            throw new IllegalStateException("This order has already been modified " + currentCount + " time(s). Maximum "
                    + maxModifications + " modification" + (maxModifications > 1 ? "s are" : " is") + " permitted.");
        }

        // 1. Resolve target vehicle type (supports upgradeToDrone shortcut)
        VehicleType targetVehicleType = order.getVehicleType();
        if (Boolean.TRUE.equals(request.getUpgradeToDrone())) {
            targetVehicleType = VehicleType.DRONE;
        } else if (request.getVehicleType() != null) {
            targetVehicleType = request.getVehicleType();
        }

        BigDecimal capacityTolerance = isVip ? new BigDecimal("1.10") : BigDecimal.ONE;
        BigDecimal maxDroneWeight = new BigDecimal("3.00").multiply(capacityTolerance).setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxDroneVolume = new BigDecimal("0.05").multiply(capacityTolerance).setScale(4, RoundingMode.HALF_UP);
        BigDecimal maxRobotWeight = new BigDecimal("15.00").multiply(capacityTolerance).setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxRobotVolume = new BigDecimal("0.30").multiply(capacityTolerance).setScale(4, RoundingMode.HALF_UP);

        // Specialized validation for drone upgrade: verify package weight capacity
        if (targetVehicleType == VehicleType.DRONE && order.getVehicleType() != VehicleType.DRONE) {
            BigDecimal currentOrReqWeight = request.getPackageWeight() != null ? request.getPackageWeight() : order.getPackageWeight();
            if (currentOrReqWeight != null && currentOrReqWeight.compareTo(maxDroneWeight) > 0) {
                throw new IllegalArgumentException("Package weight (" + currentOrReqWeight + " kg) exceeds drone maximum capacity of " + maxDroneWeight + " kg. Upgrade is not available.");
            }
        }

        // 2. Extract and validate coordinates and package specifications
        BigDecimal pLat = request.getPickupLat() != null ? request.getPickupLat() : order.getPickupLat();
        BigDecimal pLng = request.getPickupLng() != null ? request.getPickupLng() : order.getPickupLng();
        String pAddress = request.getPickupAddress() != null && !request.getPickupAddress().isBlank()
                ? request.getPickupAddress().trim() : order.getPickupAddress();

        BigDecimal dLat = request.getDropoffLat() != null ? request.getDropoffLat() : order.getDropoffLat();
        BigDecimal dLng = request.getDropoffLng() != null ? request.getDropoffLng() : order.getDropoffLng();
        String dAddress = request.getDropoffAddress() != null && !request.getDropoffAddress().isBlank()
                ? request.getDropoffAddress().trim() : order.getDropoffAddress();

        // Validate pickup and destination locations within San Francisco service area
        if (!RecommendationService.isWithinSanFrancisco(pLat, pLng)) {
            throw new IllegalArgumentException("Pickup address is outside the San Francisco service area.");
        }
        if (!RecommendationService.isWithinSanFrancisco(dLat, dLng)) {
            throw new IllegalArgumentException("Destination address is outside the San Francisco service area.");
        }

        BigDecimal weight = request.getPackageWeight() != null ? request.getPackageWeight() : order.getPackageWeight();
        BigDecimal volume = order.getPackageVolume();
        if (request.getPackageLengthCm() != null && request.getPackageWidthCm() != null && request.getPackageHeightCm() != null) {
            double l = request.getPackageLengthCm().doubleValue() / 100.0;
            double w = request.getPackageWidthCm().doubleValue() / 100.0;
            double h = request.getPackageHeightCm().doubleValue() / 100.0;
            volume = BigDecimal.valueOf(l * w * h).setScale(4, RoundingMode.HALF_UP);
        }

        // Validate package weight and volume physical limits according to target vehicle carrier type
        if (targetVehicleType == VehicleType.DRONE) {
            if (weight != null && weight.compareTo(maxDroneWeight) > 0) {
                throw new IllegalArgumentException("Package weight (" + weight + " kg) exceeds drone maximum capacity of " + maxDroneWeight + " kg.");
            }
            if (volume != null && volume.compareTo(maxDroneVolume) > 0) {
                throw new IllegalArgumentException("Package volume (" + volume + " m³) exceeds drone cargo bay limit of " + maxDroneVolume + " m³.");
            }
        } else if (targetVehicleType == VehicleType.ROBOT) {
            if (weight != null && weight.compareTo(maxRobotWeight) > 0) {
                throw new IllegalArgumentException("Package weight (" + weight + " kg) exceeds robot maximum capacity of " + maxRobotWeight + " kg.");
            }
            if (volume != null && volume.compareTo(maxRobotVolume) > 0) {
                throw new IllegalArgumentException("Package volume (" + volume + " m³) exceeds robot cargo bay limit of " + maxRobotVolume + " m³.");
            }
        }

        // 3. Invoke RecommendationService to re-evaluate route and pricing
        QuoteRequest quoteReq = QuoteRequest.builder()
                .pickupAddress(pAddress)
                .pickupLat(pLat)
                .pickupLng(pLng)
                .dropoffAddress(dAddress)
                .dropoffLat(dLat)
                .dropoffLng(dLng)
                .packageWeight(weight)
                .packageVolume(volume)
                .build();

        QuoteResponse quoteResponse = recommendationService.generateRecommendations(quoteReq, currentUser);
        final VehicleType finalTargetType = targetVehicleType;
        PlanOptionDto matchedPlan = quoteResponse.getPlans().stream()
                .filter(p -> p.getVehicleType() == finalTargetType)
                .findFirst()
                .orElseThrow(() -> {
                    if (finalTargetType == VehicleType.DRONE) {
                        return new NoVehicleAvailableException(
                                "No idle drones with sufficient battery (estimated remaining >= 10% after delivery) are currently available in the fleet. Upgrade to Drone Express cannot be completed at this time.");
                    }
                    return new NoVehicleAvailableException(
                            "No available " + finalTargetType + " option found for the updated order parameters.");
                });

        // 4. Vehicle and station dispatch reallocation:
        boolean stationChanged = !matchedPlan.getStationId().equals(order.getStationId());
        boolean typeChanged = matchedPlan.getVehicleType() != order.getVehicleType();
        boolean pickupChanged = request.getPickupLat() != null || request.getPickupLng() != null
                || (request.getPickupAddress() != null && !request.getPickupAddress().equals(order.getPickupAddress()));
        boolean dropoffChanged = request.getDropoffLat() != null || request.getDropoffLng() != null
                || (request.getDropoffAddress() != null && !request.getDropoffAddress().equals(order.getDropoffAddress()));
        boolean specChanged = (request.getPackageWeight() != null && request.getPackageWeight().compareTo(order.getPackageWeight()) != 0)
                || request.getPackageLengthCm() != null || request.getPackageWidthCm() != null || request.getPackageHeightCm() != null;

        if (stationChanged || typeChanged || pickupChanged || dropoffChanged || specChanged) {
            // Release previous vehicle back to IDLE
            if (order.getVehicleId() != null) {
                vehicleRepository.findById(order.getVehicleId()).ifPresent(oldV -> {
                    oldV.setStatus(VehicleStatus.IDLE);
                    oldV.setStatusUpdatedAt(LocalDateTime.now());
                    oldV.setCurrentSpeed(BigDecimal.ZERO);
                    oldV.setSpeedUpdatedAt(LocalDateTime.now());
                    vehicleRepository.save(oldV);
                    log.info("Released previous vehicle {} back to IDLE upon order modification.", oldV.getVehicleCode());
                });
            }

            BigDecimal requiredBattery = recommendationService.calculateRequiredInitialBattery(
                    matchedPlan.getTotalDistance(),
                    matchedPlan.getVehicleType(),
                    weight
            );

            BigDecimal checkWeight = weight.divide(capacityTolerance, 4, RoundingMode.HALF_UP);
            BigDecimal checkVolume = volume.divide(capacityTolerance, 4, RoundingMode.HALF_UP);

            // Lock new vehicle from the selected optimal station
            List<Vehicle> availableVehicles = vehicleRepository.findAvailableVehiclesForLock(
                    matchedPlan.getStationId(),
                    matchedPlan.getVehicleType(),
                    RecommendationService.DISPATCHABLE_STATUSES,
                    requiredBattery,
                    checkWeight,
                    checkVolume
            );

            if (availableVehicles.isEmpty()) {
                throw new NoVehicleAvailableException("No available idle " + matchedPlan.getVehicleType()
                        + " at station " + matchedPlan.getStationName() + " to satisfy the updated order.");
            }

            Vehicle newVehicle = availableVehicles.get(0);
            Station newStation = stationRepository.findById(matchedPlan.getStationId()).orElse(null);
            LocalDateTime now = LocalDateTime.now();

            newVehicle.setStatus(VehicleStatus.IN_DELIVERY);
            newVehicle.setStatusUpdatedAt(now);
            newVehicle.setLocationCode(Vehicle.LOCATION_NOT_AT_STATION);
            newVehicle.setCurrentSpeed(newVehicle.getCruiseSpeed());
            newVehicle.setSpeedUpdatedAt(now);
            newVehicle.setCurrentLat(newStation != null ? newStation.getLatitude() : pLat);
            newVehicle.setCurrentLng(newStation != null ? newStation.getLongitude() : pLng);
            newVehicle.setPositionUpdatedAt(now);
            vehicleRepository.save(newVehicle);

            order.setStationId(matchedPlan.getStationId());
            order.setVehicleId(newVehicle.getId());
            order.setVehicleType(matchedPlan.getVehicleType());
            order.setPlanType(matchedPlan.getPlanType());
            log.info("Reassigned vehicle {} (Station: {}) for modified order {}",
                    newVehicle.getVehicleCode(), matchedPlan.getStationName(), order.getOrderNumber());
        }

        // 5. Price recalculation and difference settlement (Surcharge / Partial Refund)
        BigDecimal oldPrice = order.getFinalPrice() != null ? order.getFinalPrice() : BigDecimal.ZERO;
        BigDecimal newPrice = matchedPlan.getFinalPrice();
        BigDecimal diff = newPrice.subtract(oldPrice);

        if (diff.compareTo(BigDecimal.ZERO) > 0) {
            // Additional surcharge payment required
            paymentRepository.findByOrderId(order.getId()).ifPresentOrElse(p -> {
                p.setAmount(newPrice);
                p.setPaidAt(LocalDateTime.now());
                paymentRepository.save(p);
                log.info("Payment updated for order {}: new total amount ${} (surcharge: ${})",
                        order.getOrderNumber(), newPrice, diff);
            }, () -> {
                String cardIdentifier = request.getPaymentMethodId() != null && !request.getPaymentMethodId().isBlank()
                        ? request.getPaymentMethodId()
                        : request.getCardNumber() != null ? request.getCardNumber() : "pm_mock_card";
                paymentService.processPayment(order.getId(), currentUser.getId(), newPrice, cardIdentifier);
            });
        } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
            // Price reduced, refund difference back to customer
            paymentRepository.findByOrderId(order.getId()).ifPresent(p -> {
                p.setAmount(newPrice);
                paymentRepository.save(p);
                log.info("Partial refund of ${} updated for modified order {}: new amount ${}",
                        diff.abs(), order.getOrderNumber(), newPrice);
            });
        }

        // 6. Persist order updates
        order.setPickupAddress(pAddress);
        order.setPickupLat(pLat);
        order.setPickupLng(pLng);
        order.setDropoffAddress(dAddress);
        order.setDropoffLat(dLat);
        order.setDropoffLng(dLng);
        order.setPackageWeight(weight);
        order.setPackageVolume(volume);
        order.setOriginPrice(matchedPlan.getOriginPrice());
        order.setDiscountAmount(matchedPlan.getDiscountAmount());
        order.setFinalPrice(newPrice);
        order.setTotalDistance(matchedPlan.getTotalDistance());
        order.setScheduledStartTime(matchedPlan.getScheduledStartTime());
        order.setEstimatedDeliveryTime(matchedPlan.getEstimatedDeliveryTime());
        int newModifiedCount = currentCount + 1;
        order.setModifiedCount(newModifiedCount);
        order.setHasBeenModified(newModifiedCount >= maxModifications);
        orderRepository.save(order);

        // 7. Record tracking event
        String diffDesc = diff.compareTo(BigDecimal.ZERO) > 0
                ? " Surcharge of $" + diff + " paid."
                : diff.compareTo(BigDecimal.ZERO) < 0 ? " Refund of $" + diff.abs() + " issued." : " No price change.";
        String modeDesc = typeChanged ? " Switched to " + matchedPlan.getVehicleType() + "." : "";

        TrackingEvent event = TrackingEvent.builder()
                .orderId(order.getId())
                .stage(TrackingStage.ORDER_UPDATED)
                .statusDescription("Order modified (" + newModifiedCount + "/" + maxModifications + " allowed)." + modeDesc + diffDesc)
                .eventLat(order.getPickupLat())
                .eventLng(order.getPickupLng())
                .eventTime(LocalDateTime.now())
                .build();
        trackingEventRepository.save(event);

        return order;
    }

    /**
     * Submits post-delivery review (only DELIVERED orders, 1 review per order)
     */

    @Transactional(rollbackFor = Exception.class)
    public OrderReviewResponse submitReview(String orderNumber, OrderReviewRequest request, User currentUser) {
        Order order = getOrderByNumber(orderNumber);

        if (currentUser != null && currentUser.getRole() != Role.ADMIN && !order.getUserId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Only the customer who placed order " + orderNumber + " can submit a review");
        }

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException("Delivery reviews can only be submitted after the order has been delivered.");
        }

        if (orderReviewRepository.existsByOrderId(order.getId())) {
            throw new IllegalStateException("A review has already been submitted for order " + orderNumber);
        }

        String reviewCode = "REV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        OrderReview review = OrderReview.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .rating(request.getRating())
                .comment(request.getComment() != null ? request.getComment().trim() : null)
                .damageReported(Boolean.TRUE.equals(request.getDamageReported()))
                .reviewCode(reviewCode)
                .createdAt(LocalDateTime.now())
                .build();

        review = orderReviewRepository.save(review);
        log.info("Review {} submitted for order {}: rating {}", reviewCode, orderNumber, request.getRating());

        return OrderReviewResponse.builder()
                .orderId(order.getOrderNumber())
                .reviewId(reviewCode)
                .rating(review.getRating())
                .comment(review.getComment())
                .damageReported(review.getDamageReported())
                .createdAt(review.getCreatedAt())
                .message("Review submitted successfully. Thank you for your feedback!")
                .build();
    }

    public OrderReviewResponse getReview(String orderNumber, User currentUser) {
        Order order = getAccessibleOrder(orderNumber, currentUser);
        OrderReview review = orderReviewRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No review found for order " + orderNumber));

        return OrderReviewResponse.builder()
                .orderId(order.getOrderNumber())
                .reviewId(review.getReviewCode())
                .rating(review.getRating())
                .comment(review.getComment())
                .damageReported(review.getDamageReported())
                .createdAt(review.getCreatedAt())
                .message("Review retrieved successfully.")
                .build();
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

        boolean isVip = (currentUser != null && currentUser.isVip())
                || userRepository.findById(order.getUserId()).map(User::isVip).orElse(false);
        int maxModifications = isVip ? 2 : 1;
        order.setMaxModificationsAllowed(maxModifications);

        // Dynamically evaluate drone upgrade availability for frontend display:
        // Must be a robot order, modifications remaining, in pre-pickup phase, weight within drone limit (with VIP tolerance),
        // and must have an idle drone with estimated battery remaining >= 10% after completing delivery.
        int currentModifiedCount = order.getModifiedCount() != null ? order.getModifiedCount() : (Boolean.TRUE.equals(order.getHasBeenModified()) ? 1 : 0);
        BigDecimal maxWeight = new BigDecimal("3.00").multiply(isVip ? new BigDecimal("1.10") : BigDecimal.ONE);

        boolean canUpgrade = order.getVehicleType() == VehicleType.ROBOT
                && currentModifiedCount < maxModifications
                && (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.PICKING_UP || order.getStatus() == OrderStatus.PENDING_PAYMENT)
                && (order.getPackageWeight() != null && order.getPackageWeight().compareTo(maxWeight) <= 0);

        if (canUpgrade) {
            QuoteRequest quoteReq = QuoteRequest.builder()
                    .pickupAddress(order.getPickupAddress() != null ? order.getPickupAddress() : "San Francisco Pickup")
                    .pickupLat(order.getPickupLat())
                    .pickupLng(order.getPickupLng())
                    .dropoffAddress(order.getDropoffAddress() != null ? order.getDropoffAddress() : "San Francisco Dropoff")
                    .dropoffLat(order.getDropoffLat())
                    .dropoffLng(order.getDropoffLng())
                    .packageWeight(order.getPackageWeight() != null ? order.getPackageWeight() : new BigDecimal("1.5"))
                    .packageVolume(order.getPackageVolume() != null ? order.getPackageVolume() : new BigDecimal("0.02"))
                    .build();

            try {
                QuoteResponse quoteResponse = recommendationService.generateRecommendations(quoteReq, currentUser);
                boolean hasViableDrone = quoteResponse.getPlans().stream()
                        .anyMatch(p -> p.getVehicleType() == VehicleType.DRONE);
                order.setDroneUpgradeAvailable(hasViableDrone);
            } catch (Exception e) {
                log.warn("Failed to evaluate drone upgrade eligibility for order {}: {}", order.getOrderNumber(), e.getMessage());
                order.setDroneUpgradeAvailable(false);
            }
        } else {
            order.setDroneUpgradeAvailable(false);
        }

        return order;
    }


    public List<Order> getUserOrders(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 时间线判定包裹是否已被取件：与追踪时间线（TrackingService）同源——
     * 按「站→取件→送达→返站」里程等比占比切分，过了取件里程碑才禁止取消/改单。
     * estimatedDeliveryTime 语义为包裹送达时刻，返站段按占比补足成整程后判定。
     *
     * <p>取件里程碑同时受 {@link #PICKUP_CANCELLATION_GRACE_SECONDS} 保护：
     * 自投类订单（首段里程为 0）不会被立刻判成「已取件」。
     */
    private boolean packagePickedUpByTimeline(Order order) {
        if (order.getStatus() == OrderStatus.IN_TRANSIT) {
            return true;
        }
        TripTimeline timeline = tripTimeline(order);
        if (timeline == null) {
            return false;
        }
        return elapsedTripSeconds(order) >= Math.max(timeline.pickupSeconds, PICKUP_CANCELLATION_GRACE_SECONDS);
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
