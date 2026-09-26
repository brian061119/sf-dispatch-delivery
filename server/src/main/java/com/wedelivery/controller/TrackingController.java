package com.wedelivery.controller;

import com.wedelivery.dto.TrackingResponse;
import com.wedelivery.entity.User;
import com.wedelivery.service.OrderService;
import com.wedelivery.service.TrackingService;
import lombok.RequiredArgsConstructor;
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
        map.put("orderId", res.getOrderNumber());
        map.put("status", res.getOrderStatus().name());
        map.put("vehicleType", res.getVehicleType().name());
        map.put("currentLat", res.getCurrentLat());
        map.put("currentLng", res.getCurrentLng());
        map.put("estimatedArrival", LocalDateTime.now().plusMinutes(res.getEtaMinutesRemaining()).format(DateTimeFormatter.ISO_DATE_TIME));
        // 附加详细进度
        map.put("progressPercent", res.getProgressPercent());
        map.put("currentStageDescription", res.getCurrentStageDescription());
        map.put("events", res.getEvents());
        return map;
    }
}
