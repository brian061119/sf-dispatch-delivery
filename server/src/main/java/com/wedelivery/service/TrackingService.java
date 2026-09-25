package com.wedelivery.service;

import com.wedelivery.dto.TrackingResponse;
import com.wedelivery.entity.Order;
import com.wedelivery.entity.Station;
import com.wedelivery.entity.TrackingEvent;
import com.wedelivery.entity.Vehicle;
import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.entity.enums.TrackingStage;
import com.wedelivery.entity.enums.VehicleStatus;
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

    @Transactional
    public TrackingResponse trackOrder(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderNumber));

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

        // Compute elapsed-time progress ratio
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = order.getScheduledStartTime();
        LocalDateTime deliveryTime = order.getEstimatedDeliveryTime();

        // Total trip duration, floored at 60s to guard against divide-by-zero
        long totalSeconds = Math.max(60, Duration.between(startTime, deliveryTime).getSeconds());
        long elapsedSeconds = Math.max(0, Duration.between(startTime, now).getSeconds());

        double overallRatio = Math.min(1.0, (double) elapsedSeconds / totalSeconds);

        // Define waypoint coordinates
        BigDecimal sLat = station.getLatitude();
        BigDecimal sLng = station.getLongitude();
        BigDecimal pLat = Boolean.TRUE.equals(order.getIsStationPickup()) ? sLat : order.getPickupLat();
        BigDecimal pLng = Boolean.TRUE.equals(order.getIsStationPickup()) ? sLng : order.getPickupLng();
        BigDecimal dLat = order.getDropoffLat();
        BigDecimal dLng = order.getDropoffLng();

        TrackingStage currentStage;
        String stageDesc;
        BigDecimal currentLat;
        BigDecimal currentLng;
        OrderStatus newOrderStatus = order.getStatus();

        // Split the trip into 3 weighted legs:
        // 0.0 ~ 0.25: Station -> Pickup (TO_PICKUP)
        // 0.25 ~ 0.75: Pickup -> Dropoff (TO_DROPOFF)
        // 0.75 ~ 1.00: Dropoff -> Station (RETURNING)
        // >= 1.00: COMPLETED
        if (overallRatio < 0.25) {
            currentStage = TrackingStage.TO_PICKUP;
            stageDesc = "Vehicle is en route to the pickup location";
            newOrderStatus = OrderStatus.PICKING_UP;
            double t = overallRatio / 0.25;
            currentLat = interpolate(sLat, pLat, t);
            currentLng = interpolate(sLng, pLng, t);
        } else if (overallRatio < 0.75) {
            currentStage = TrackingStage.TO_DROPOFF;
            stageDesc = "Package picked up successfully, speeding to the destination";
            newOrderStatus = OrderStatus.IN_TRANSIT;
            double t = (overallRatio - 0.25) / 0.50;
            currentLat = interpolate(pLat, dLat, t);
            currentLng = interpolate(pLng, dLng, t);
        } else if (overallRatio < 1.00) {
            currentStage = TrackingStage.RETURNING;
            stageDesc = "Package delivered! Vehicle is returning to the station's charging bay";
            newOrderStatus = OrderStatus.DELIVERED;
            double t = (overallRatio - 0.75) / 0.25;
            currentLat = interpolate(dLat, sLat, t);
            currentLng = interpolate(dLng, sLng, t);
        } else {
            currentStage = TrackingStage.COMPLETED;
            stageDesc = "Delivery lifecycle complete, vehicle docked and charging.";
            newOrderStatus = OrderStatus.DELIVERED;
            currentLat = sLat;
            currentLng = sLng;

            // Update vehicle status back to IDLE / CHARGING
            if (vehicle != null && vehicle.getStatus() == VehicleStatus.BUSY) {
                vehicle.setStatus(VehicleStatus.IDLE);
                vehicleRepository.save(vehicle);
            }
            if (order.getActualDeliveryTime() == null) {
                order.setActualDeliveryTime(now);
            }
        }

        // Backfill any stage(s) skipped between polls, then record the current one.
        // Each stage is recorded at its own fixed boundary point rather than the
        // live-interpolated position, so a backfilled entry looks identical to one
        // recorded live.
        ensureMilestonesRecorded(order.getId(), currentStage, sLat, sLng, pLat, pLng, dLat, dLng);

        // Persist the updated status
        if (order.getStatus() != newOrderStatus) {
            order.setStatus(newOrderStatus);
            orderRepository.save(order);
        }

        long remainingSec = Math.max(0, totalSeconds - elapsedSeconds);
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
                .orderNumber(order.getOrderNumber())
                .orderStatus(order.getStatus())
                .vehicleType(order.getVehicleType())
                .vehicleCode(vehicleCode)
                .currentStage(currentStage)
                .currentStageDescription(stageDesc)
                .currentLat(currentLat)
                .currentLng(currentLng)
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

    private BigDecimal interpolate(BigDecimal start, BigDecimal end, double t) {
        double s = start.doubleValue();
        double e = end.doubleValue();
        double val = s + t * (e - s);
        return BigDecimal.valueOf(val).setScale(7, RoundingMode.HALF_UP);
    }

    private TrackingResponse buildStaticResponse(Order order, Station station, String vehicleCode) {
        return TrackingResponse.builder()
                .orderNumber(order.getOrderNumber())
                .orderStatus(order.getStatus())
                .vehicleType(order.getVehicleType())
                .vehicleCode(vehicleCode)
                .currentStage(TrackingStage.TO_PICKUP)
                .currentStageDescription("Order not paid yet or has been cancelled")
                .currentLat(station.getLatitude())
                .currentLng(station.getLongitude())
                .progressPercent(BigDecimal.ZERO)
                .etaMinutesRemaining(0)
                .events(List.of())
                .build();
    }
}
