package com.wedelivery.controller;

import com.wedelivery.dto.TrackingResponse;
import com.wedelivery.entity.User;
import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.service.OrderService;
import com.wedelivery.service.TrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;
    private final OrderService orderService;

    // 契约路径: GET /api/orders/:orderId/tracking
    // 需登录: 仅下单用户本人或管理员可查看 (订单号可被推测，不能作为公开凭证)
    @GetMapping("/api/orders/{orderNumber}/tracking")
    public ResponseEntity<Map<String, Object>> getTrackingContract(
            @PathVariable String orderNumber,
            @AuthenticationPrincipal User currentUser
    ) {
        orderService.getAccessibleOrder(orderNumber, currentUser);
        return ResponseEntity.ok(toContractResponse(trackingService.trackOrder(orderNumber)));
    }

    // 公开追踪: GET /api/tracking/:trackingCode
    // 任何持有随机追踪码的人无需登录即可只读查看状态与位置 (见 SecurityConfig，仅开放 GET)
    @GetMapping("/api/tracking/{trackingCode}")
    public ResponseEntity<Map<String, Object>> trackByCode(@PathVariable String trackingCode) {
        return ResponseEntity.ok(toContractResponse(trackingService.trackByTrackingCode(trackingCode)));
    }

    private Map<String, Object> toContractResponse(TrackingResponse res) {
        Map<String, Object> map = new HashMap<>();
        // Contract semantics: "orderId" in order endpoints is the SFORD order
        // number (cancel/update/track all look up by it). Keep that mapping
        // intact; expose the numeric PK separately as orderDbId.
        map.put("orderId", res.getOrderNumber());
        map.put("orderNumber", res.getOrderNumber());
        map.put("orderDbId", res.getOrderId());
        map.put("status", res.getOrderStatus().name());
        map.put("vehicleType", res.getVehicleType().name());
        map.put("vehicleCode", res.getVehicleCode());
        map.put("currentStage", res.getCurrentStage() != null ? res.getCurrentStage().name() : null);
        map.put("currentLat", res.getCurrentLat());
        map.put("currentLng", res.getCurrentLng());
        map.put("pickupLat", res.getPickupLat());
        map.put("pickupLng", res.getPickupLng());
        map.put("destinationLat", res.getDestinationLat());
        map.put("destinationLng", res.getDestinationLng());
        map.put("routePolyline", res.getRoutePolyline());
        // 已取消的订单没有"预计送达"——now + 0 分钟会显示成一个假 ETA（恰好
        // 等于查询时刻），对用户是误导；输出 null，前端显示为 "—"。
        map.put("estimatedArrival", res.getOrderStatus() == OrderStatus.CANCELLED
                ? null
                : LocalDateTime.now().plusMinutes(res.getEtaMinutesRemaining()).format(DateTimeFormatter.ISO_DATE_TIME));
        // Attach detailed progress fields
        map.put("progressPercent", res.getProgressPercent());
        map.put("currentStageDescription", res.getCurrentStageDescription());
        map.put("events", res.getEvents());
        return map;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotFound(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}
