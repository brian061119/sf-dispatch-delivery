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
import com.wedelivery.repository.OrderRepository;
import com.wedelivery.repository.StationRepository;
import com.wedelivery.repository.TrackingEventRepository;
import com.wedelivery.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
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

        // 若订单尚未付款
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT || order.getStatus() == OrderStatus.CANCELLED) {
            return buildStaticResponse(order, station, vehicleCode);
        }

        // 计算物理时间推进比例
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = order.getScheduledStartTime();
        LocalDateTime deliveryTime = order.getEstimatedDeliveryTime();

        // 默认全流程总时间 (若未设定则按 15 分钟模拟)
        long totalSeconds = Math.max(60, Duration.between(startTime, deliveryTime).getSeconds());
        long elapsedSeconds = Math.max(0, Duration.between(startTime, now).getSeconds());

        double overallRatio = Math.min(1.0, (double) elapsedSeconds / totalSeconds);

        // 航段点定义
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

        // 划分 3 个航段权重：
        // 0.0 ~ 0.25: Station -> Pickup (TO_PICKUP)
        // 0.25 ~ 0.75: Pickup -> Dropoff (TO_DROPOFF)
        // 0.75 ~ 1.00: Dropoff -> Station (RETURNING)
        // >= 1.00: COMPLETED
        if (overallRatio < 0.25) {
            currentStage = TrackingStage.TO_PICKUP;
            stageDesc = "载具正在前往取件地点中";
            newOrderStatus = OrderStatus.PICKING_UP;
            double t = overallRatio / 0.25;
            currentLat = interpolate(sLat, pLat, t);
            currentLng = interpolate(sLng, pLng, t);
            recordMilestoneIfAbsent(order.getId(), TrackingStage.TO_PICKUP, "载具已出发，正平稳航向取件地", currentLat, currentLng);
        } else if (overallRatio < 0.75) {
            currentStage = TrackingStage.TO_DROPOFF;
            stageDesc = "包裹已成功揽件，正在飞速配送至目的地";
            newOrderStatus = OrderStatus.IN_TRANSIT;
            double t = (overallRatio - 0.25) / 0.50;
            currentLat = interpolate(pLat, dLat, t);
            currentLng = interpolate(pLng, dLng, t);
            recordMilestoneIfAbsent(order.getId(), TrackingStage.TO_DROPOFF, "已完成取件，正在送往收件地址", currentLat, currentLng);
        } else if (overallRatio < 1.00) {
            currentStage = TrackingStage.RETURNING;
            stageDesc = "包裹已送达！载具正在返航回站点充电机舱";
            newOrderStatus = OrderStatus.DELIVERED;
            double t = (overallRatio - 0.75) / 0.25;
            currentLat = interpolate(dLat, sLat, t);
            currentLng = interpolate(dLng, sLng, t);
            recordMilestoneIfAbsent(order.getId(), TrackingStage.RETURNING, "收件人已确认签收，载具返航中", dLat, dLng);
        } else {
            currentStage = TrackingStage.COMPLETED;
            stageDesc = "配送全生命周期圆满完成，载具已停泊充电。";
            newOrderStatus = OrderStatus.DELIVERED;
            currentLat = sLat;
            currentLng = sLng;
            recordMilestoneIfAbsent(order.getId(), TrackingStage.COMPLETED, "载具已安全返回分配中心泊位", sLat, sLng);

            if (order.getActualDeliveryTime() == null) {
                order.setActualDeliveryTime(now);
            }
        }

        // 同步机器实时信息：位置与速度随航段推进，返站后归位并恢复待命。
        // 本方法即「机器实时信息 → 实时追踪系统」的接入口，追踪侧不必再自行推算载具坐标。
        if (vehicle != null) {
            boolean backAtStation = currentStage == TrackingStage.COMPLETED;
            vehicle.setCurrentLat(currentLat);
            vehicle.setCurrentLng(currentLng);
            vehicle.setPositionUpdatedAt(now);
            vehicle.setLocationCode(backAtStation ? station.getId().intValue() : Vehicle.LOCATION_NOT_AT_STATION);
            if (backAtStation) {
                vehicle.setCurrentSpeed(BigDecimal.ZERO);
                if (vehicle.getStatus() == VehicleStatus.IN_DELIVERY) {
                    vehicle.setStatus(VehicleStatus.IDLE);
                    vehicle.setStatusUpdatedAt(now);
                }
            } else {
                vehicle.setCurrentSpeed(vehicle.getCruiseSpeed());
            }
            vehicle.setSpeedUpdatedAt(now);
            vehicleRepository.save(vehicle);
        }

        // 状态落库更新 (已签收的订单不再被模拟进度回退为 IN_TRANSIT 等状态)
        if (order.getStatus() != newOrderStatus && order.getStatus() != OrderStatus.DELIVERED) {
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

    private void recordMilestoneIfAbsent(Long orderId, TrackingStage stage, String desc, BigDecimal lat, BigDecimal lng) {
        List<TrackingEvent> events = trackingEventRepository.findByOrderIdOrderByEventTimeAsc(orderId);
        boolean exists = events.stream().anyMatch(e -> e.getStage() == stage);
        if (!exists) {
            TrackingEvent event = TrackingEvent.builder()
                    .orderId(orderId)
                    .stage(stage)
                    .statusDescription(desc)
                    .eventLat(lat)
                    .eventLng(lng)
                    .eventTime(LocalDateTime.now())
                    .build();
            trackingEventRepository.save(event);
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
                .currentStageDescription("订单尚未支付或已取消")
                .currentLat(station.getLatitude())
                .currentLng(station.getLongitude())
                .progressPercent(BigDecimal.ZERO)
                .etaMinutesRemaining(0)
                .events(List.of())
                .build();
    }
}
