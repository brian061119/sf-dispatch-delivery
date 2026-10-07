package com.wedelivery.service;

import com.wedelivery.dto.TrackingResponse;
import com.wedelivery.entity.Order;
import com.wedelivery.entity.Station;
import com.wedelivery.entity.TrackingEvent;
import com.wedelivery.entity.Vehicle;
import com.wedelivery.exception.ResourceNotFoundException;
import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.entity.enums.TrackingStage;
import com.wedelivery.entity.enums.VehicleStatus;
import com.wedelivery.entity.enums.VehicleType;
import com.wedelivery.repository.OrderRepository;
import com.wedelivery.repository.StationRepository;
import com.wedelivery.repository.TrackingEventRepository;
import com.wedelivery.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrackingService {

    private final OrderRepository orderRepository;
    private final StationRepository stationRepository;
    private final VehicleRepository vehicleRepository;
    private final TrackingEventRepository trackingEventRepository;
    private final RouteService routeService;

    @Transactional
    public TrackingResponse trackOrder(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));
        return track(order);
    }

    @Transactional
    public TrackingResponse trackByTrackingCode(String trackingCode) {
        Order order = orderRepository.findByTrackingCode(trackingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Tracking code not found: " + trackingCode));
        return track(order);
    }

    private TrackingResponse track(Order order) {
        Station station = stationRepository.findById(order.getStationId())
                .orElseThrow(() -> new IllegalStateException("Station not found for order"));

        Vehicle vehicle = order.getVehicleId() != null
                ? vehicleRepository.findById(order.getVehicleId()).orElse(null)
                : null;

        String vehicleCode = vehicle != null ? vehicle.getVehicleCode() : "N/A";

        // Order hasn't started moving yet (unpaid or cancelled)
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT || order.getStatus() == OrderStatus.CANCELLED) {
            return buildStaticResponse(order, station, vehicleCode);
        }

        // Define waypoint coordinates
        BigDecimal sLat = station.getLatitude();
        BigDecimal sLng = station.getLongitude();
        BigDecimal pLat = Boolean.TRUE.equals(order.getIsStationPickup()) ? sLat : order.getPickupLat();
        BigDecimal pLng = Boolean.TRUE.equals(order.getIsStationPickup()) ? sLng : order.getPickupLng();
        BigDecimal dLat = order.getDropoffLat();
        BigDecimal dLng = order.getDropoffLng();

        // Compute elapsed-time progress ratio against the full closed loop.
        // estimatedDeliveryTime is the customer-facing moment the package
        // reaches the dropoff (see RecommendationService); the return leg is
        // appended proportionally (by per-leg distance) so the vehicle keeps
        // animating a complete station->pickup->dropoff->station loop.
        double[] milestones = routeService.milestoneFractions(
                sLat, sLng, pLat, pLng, dLat, dLng, order.getVehicleType());
        double pickupFraction = milestones[0];
        double deliveryFraction = milestones[1];

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = order.getScheduledStartTime();
        LocalDateTime deliveryTime = order.getEstimatedDeliveryTime();

        long deliverySeconds = Math.max(60, Duration.between(startTime, deliveryTime).getSeconds());
        long totalSeconds = Math.max(deliverySeconds, Math.round(deliverySeconds / deliveryFraction));
        long elapsedSeconds = Math.max(0, Duration.between(startTime, now).getSeconds());

        double overallRatio = Math.min(1.0, (double) elapsedSeconds / totalSeconds);
        // 包裹实际送达时刻：时间线上「站→取件→送达」走完的那一瞬间
        LocalDateTime deliveryMoment = startTime.plusSeconds(deliverySeconds);

        // 三段航程的沿道路几何折线（接入 OSRM 后机器人沿街道行驶，不再直线穿楼；
        // 无人机按 VehicleType 走直线，离线/失败时自动退化为两点直线）。
        VehicleType vehicleType = order.getVehicleType();
        List<double[]> legToPickup = routeService.routeGeometry(
                sLat.doubleValue(), sLng.doubleValue(), pLat.doubleValue(), pLng.doubleValue(), vehicleType);
        List<double[]> legToDropoff = routeService.routeGeometry(
                pLat.doubleValue(), pLng.doubleValue(), dLat.doubleValue(), dLng.doubleValue(), vehicleType);
        List<double[]> legReturning = routeService.routeGeometry(
                dLat.doubleValue(), dLng.doubleValue(), sLat.doubleValue(), sLng.doubleValue(), vehicleType);

        TrackingStage currentStage;
        String stageDesc;
        BigDecimal currentLat;
        BigDecimal currentLng;
        OrderStatus newOrderStatus = order.getStatus();

        // Split the trip by distance-proportional milestones of the full loop:
        // 0.0 ~ pickupFraction  : Station -> Pickup (TO_PICKUP)
        // pickupFraction ~ deliveryFraction : Pickup -> Dropoff (TO_DROPOFF)
        // deliveryFraction ~ 1.00 : Dropoff -> Station (RETURNING)
        // >= 1.00: COMPLETED
        // 顾客自投等情形下首段占比可为 0，该阶段宽度为 0 会被直接跳过（分支永不进入），
        // 因此下面的插值分母均不会为 0，无需额外兜底。
        if (overallRatio < pickupFraction) {
            currentStage = TrackingStage.TO_PICKUP;
            stageDesc = "Vehicle is en route to the pickup location";
            newOrderStatus = OrderStatus.PICKING_UP;
            double t = overallRatio / pickupFraction;
            double[] point = routeService.pointAlongRoute(legToPickup, t);
            currentLat = coordinate(point, 0, sLat);
            currentLng = coordinate(point, 1, sLng);
        } else if (overallRatio < deliveryFraction) {
            currentStage = TrackingStage.TO_DROPOFF;
            stageDesc = "Package picked up successfully, speeding to the destination";
            newOrderStatus = OrderStatus.IN_TRANSIT;
            double t = (overallRatio - pickupFraction) / (deliveryFraction - pickupFraction);
            double[] point = routeService.pointAlongRoute(legToDropoff, t);
            currentLat = coordinate(point, 0, pLat);
            currentLng = coordinate(point, 1, pLng);
        } else if (overallRatio < 1.00) {
            currentStage = TrackingStage.RETURNING;
            stageDesc = "Package delivered! Vehicle is returning to the station's charging bay";
            newOrderStatus = OrderStatus.DELIVERED;
            double t = (overallRatio - deliveryFraction) / (1.0 - deliveryFraction);
            double[] point = routeService.pointAlongRoute(legReturning, t);
            currentLat = coordinate(point, 0, dLat);
            currentLng = coordinate(point, 1, dLng);
        } else {
            currentStage = TrackingStage.COMPLETED;
            stageDesc = "Delivery lifecycle complete, vehicle docked and charging.";
            newOrderStatus = OrderStatus.DELIVERED;
            currentLat = sLat;
            currentLng = sLng;
        }

        // 签收时刻在进入 RETURNING（客户确认收货）时即告确定，按时间线精确回填；
        // 不再等到车辆返站（COMPLETED）才记录，那会把送达时间推迟一整段返程。
        if (currentStage.ordinal() >= TrackingStage.RETURNING.ordinal() && order.getActualDeliveryTime() == null) {
            order.setActualDeliveryTime(deliveryMoment.isAfter(now) ? now : deliveryMoment);
        }

        // 同步机器实时信息：位置与速度随航段推进，返站后归位并按电量进入待命/充电。
        // 本方法即「机器实时信息 → 实时追踪系统」的接入口，追踪侧不必再自行推算载具坐标。
        if (vehicle != null) {
            boolean backAtStation = currentStage == TrackingStage.COMPLETED;

            // 行程实际耗电：按本次位移的直线里程 × 类型能耗率 × 载重加权，逐段累扣。
            // 与派单准入（RecommendationService）共用同一套耗电模型，故「预测耗电」与「真实耗电」一致。
            BigDecimal prevLat = vehicle.getCurrentLat();
            BigDecimal prevLng = vehicle.getCurrentLng();
            if (prevLat != null && prevLng != null) {
                double segmentKm = routeService.calculateStraightDistance(
                        prevLat.doubleValue(), prevLng.doubleValue(),
                        currentLat.doubleValue(), currentLng.doubleValue());
                if (segmentKm > 0) {
                    double energyRate = vehicle.getVehicleType().getEnergyRatePercentPerKm().doubleValue();
                    double weightFactor = RecommendationService.loadWeightFactor(
                            order.getPackageWeight() != null ? order.getPackageWeight().doubleValue() : 0.0,
                            vehicle.getMaxWeight() != null ? vehicle.getMaxWeight().doubleValue() : 0.0);
                    BigDecimal drained = BigDecimal.valueOf(segmentKm * energyRate * weightFactor);
                    BigDecimal nextBattery = vehicle.getBatteryLevel() == null
                            ? BigDecimal.ZERO
                            : vehicle.getBatteryLevel().subtract(drained);
                    vehicle.setBatteryLevel(nextBattery.signum() < 0
                            ? BigDecimal.ZERO
                            : nextBattery.setScale(2, RoundingMode.HALF_UP));
                }
            }

            vehicle.setCurrentLat(currentLat);
            vehicle.setCurrentLng(currentLng);
            vehicle.setPositionUpdatedAt(now);
            vehicle.setLocationCode(backAtStation ? station.getId().intValue() : Vehicle.LOCATION_NOT_AT_STATION);
            if (backAtStation) {
                vehicle.setCurrentSpeed(BigDecimal.ZERO);
                if (vehicle.getStatus() == VehicleStatus.IN_DELIVERY) {
                    // 返站默认在配送站充电：电量未满转 CHARGING，由模拟器逐 tick 补电，充满再待命
                    vehicle.setStatus(vehicle.restingStatus());
                    vehicle.setStatusUpdatedAt(now);
                }
            } else {
                vehicle.setCurrentSpeed(vehicle.getCruiseSpeed());
            }
            vehicle.setSpeedUpdatedAt(now);
            vehicleRepository.save(vehicle);
        }

        // Backfill any stage(s) skipped between polls, then record the current one.
        // Each stage is recorded at its own fixed boundary point rather than the
        // live-interpolated position, so a backfilled entry looks identical to one
        // recorded live.
        ensureMilestonesRecorded(order.getId(), currentStage, sLat, sLng, pLat, pLng, dLat, dLng);

        // 状态落库更新 (已签收的订单不再被模拟进度回退为 IN_TRANSIT 等状态)
        if (order.getStatus() != newOrderStatus && order.getStatus() != OrderStatus.DELIVERED) {
            order.setStatus(newOrderStatus);
            orderRepository.save(order);
        }

        // 剩余等待时间以「包裹送达」为终点（客户视角），不含返站段；
        // 已送达/取消的订单由 Controller 输出 null。
        long remainingSec = Math.max(0, deliverySeconds - elapsedSeconds);
        int etaMinutesRemaining = (int) Math.ceil((double) remainingSec / 60.0);
        BigDecimal progressPercent = BigDecimal.valueOf(overallRatio * 100).setScale(1, RoundingMode.HALF_UP);

        List<TrackingEvent> events = trackingEventRepository.findByOrderIdOrderByEventTimeAsc(order.getId());
        List<TrackingResponse.TrackingEventDto> eventDtos = events.stream()
                .map(e -> TrackingResponse.TrackingEventDto.builder()
                        .stage(e.getStage())
                        .statusDescription(e.getStatusDescription())
                        .eventLat(e.getEventLat())
                        .eventLng(e.getEventLng())
                        .eventTime(e.getEventTime())
                        .build())
                .collect(Collectors.toList());

        return TrackingResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderStatus(order.getStatus())
                .vehicleType(order.getVehicleType())
                .vehicleCode(vehicleCode)
                .currentStage(currentStage)
                .currentStageDescription(stageDesc)
                .currentLat(currentLat)
                .currentLng(currentLng)
                .pickupLat(pLat)
                .pickupLng(pLng)
                .destinationLat(dLat)
                .destinationLng(dLng)
                .routePolyline(concatLegs(legToPickup, legToDropoff, legReturning))
                .progressPercent(progressPercent)
                .etaMinutesRemaining(etaMinutesRemaining)
                .events(eventDtos)
                .build();
    }

    // Walks every stage from TO_PICKUP up to currentStage (inclusive) and records
    // whichever ones are still missing. On a normally-paced poll this only ever
    // finds the single newest stage missing; if polling was sparse enough to skip
    // past one or more stages entirely, this is what backfills them so the
    // milestone history has no gaps regardless of how often the client checked in.
    private void ensureMilestonesRecorded(Long orderId, TrackingStage currentStage,
                                           BigDecimal sLat, BigDecimal sLng,
                                           BigDecimal pLat, BigDecimal pLng,
                                           BigDecimal dLat, BigDecimal dLng) {
        List<TrackingEvent> recorded = trackingEventRepository.findByOrderIdOrderByEventTimeAsc(orderId);
        Set<TrackingStage> recordedStages = recorded.stream()
                .map(TrackingEvent::getStage)
                .collect(Collectors.toSet());

        if (currentStage.ordinal() >= TrackingStage.TO_PICKUP.ordinal() && !recordedStages.contains(TrackingStage.TO_PICKUP)) {
            recordMilestone(orderId, TrackingStage.TO_PICKUP, "Vehicle has departed and is steadily heading to the pickup point", sLat, sLng);
        }
        if (currentStage.ordinal() >= TrackingStage.TO_DROPOFF.ordinal() && !recordedStages.contains(TrackingStage.TO_DROPOFF)) {
            recordMilestone(orderId, TrackingStage.TO_DROPOFF, "Pickup complete, en route to the delivery address", pLat, pLng);
        }
        if (currentStage.ordinal() >= TrackingStage.RETURNING.ordinal() && !recordedStages.contains(TrackingStage.RETURNING)) {
            recordMilestone(orderId, TrackingStage.RETURNING, "Recipient confirmed receipt, vehicle is returning", dLat, dLng);
        }
        if (currentStage.ordinal() >= TrackingStage.COMPLETED.ordinal() && !recordedStages.contains(TrackingStage.COMPLETED)) {
            recordMilestone(orderId, TrackingStage.COMPLETED, "Vehicle has safely returned to its station bay", sLat, sLng);
        }
    }

    private void recordMilestone(Long orderId, TrackingStage stage, String desc, BigDecimal lat, BigDecimal lng) {
        TrackingEvent event = TrackingEvent.builder()
                .orderId(orderId)
                .stage(stage)
                .statusDescription(desc)
                .eventLat(lat)
                .eventLng(lng)
                .eventTime(LocalDateTime.now())
                .build();
        try {
            // Flush immediately so a concurrent insert of the same (orderId, stage)
            // trips the DB unique constraint right here instead of at the enclosing
            // transaction's commit, where it would be too late to catch cleanly.
            trackingEventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Milestone for order {} stage {} was already recorded by a concurrent request", orderId, stage);
        }
    }

    /** 拼接三段航程折线，去掉相邻段之间重复的交界点。 */
    private List<double[]> concatLegs(List<double[]>... legs) {
        List<double[]> merged = new ArrayList<>();
        for (List<double[]> leg : legs) {
            if (leg == null || leg.isEmpty()) {
                continue;
            }
            for (int i = 0; i < leg.size(); i++) {
                if (i == 0 && !merged.isEmpty()) {
                    continue; // 交界点已由前一段的末尾提供
                }
                merged.add(leg.get(i));
            }
        }
        return merged;
    }

    /**
     * 从沿道路折线的插值点 {lat, lng} 中取指定分量（0=纬度, 1=经度）。
     * 折线缺失时退回该航段的起点坐标，保证追踪接口永远有位置可返回。
     */
    private BigDecimal coordinate(double[] point, int index, BigDecimal fallback) {
        double value = point == null ? fallback.doubleValue() : point[index];
        return BigDecimal.valueOf(value).setScale(7, RoundingMode.HALF_UP);
    }

    private TrackingResponse buildStaticResponse(Order order, Station station, String vehicleCode) {
        // 替代「一切归零、只给站点坐标」的旧静态响应：取消/未支付订单依然应该
        // 能在地图上看到这单"从哪到哪"（起终点 marker），并且能看到真实的事件
        // 历史（尤其是 cancelOrder 落库的 CANCELLED 事件，含手续费与退款金额）。
        boolean cancelled = order.getStatus() == OrderStatus.CANCELLED;
        boolean stationPickup = Boolean.TRUE.equals(order.getIsStationPickup());
        BigDecimal pLat = stationPickup ? station.getLatitude() : order.getPickupLat();
        BigDecimal pLng = stationPickup ? station.getLongitude() : order.getPickupLng();

        List<TrackingEvent> events = trackingEventRepository.findByOrderIdOrderByEventTimeAsc(order.getId());
        List<TrackingResponse.TrackingEventDto> eventDtos = events.stream()
                .map(e -> TrackingResponse.TrackingEventDto.builder()
                        .stage(e.getStage())
                        .statusDescription(e.getStatusDescription())
                        .eventLat(e.getEventLat())
                        .eventLng(e.getEventLng())
                        .eventTime(e.getEventTime())
                        .build())
                .collect(Collectors.toList());

        // 未支付订单的 ETA 用下单时约定的 estimatedDeliveryTime 换算成剩余分钟；
        // 取消订单没有 ETA（Controller 对 CANCELLED 不输出 estimatedArrival）。
        int etaMinutesRemaining = 0;
        if (!cancelled && order.getEstimatedDeliveryTime() != null) {
            etaMinutesRemaining = (int) Math.max(0, Duration.between(
                    LocalDateTime.now(), order.getEstimatedDeliveryTime()).toMinutes());
        }

        return TrackingResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderStatus(order.getStatus())
                .vehicleType(order.getVehicleType())
                .vehicleCode(vehicleCode)
                .currentStage(TrackingStage.TO_PICKUP)
                .currentStageDescription(cancelled
                        ? "This order has been cancelled"
                        : "Waiting for payment — the vehicle is on standby at its station")
                // CANCELLED 不再返回位置：车辆已被释放/改派，任何坐标都是
                // 伪装的"车在这里"；未支付时车辆确实停在站点，返回站点坐标。
                .currentLat(cancelled ? null : station.getLatitude())
                .currentLng(cancelled ? null : station.getLongitude())
                .pickupLat(pLat)
                .pickupLng(pLng)
                .destinationLat(order.getDropoffLat())
                .destinationLng(order.getDropoffLng())
                .progressPercent(BigDecimal.ZERO)
                .etaMinutesRemaining(etaMinutesRemaining)
                .events(eventDtos)
                .build();
    }
}
