package com.wedelivery.controller;

import com.wedelivery.dto.TrackingResponse;
import com.wedelivery.service.TrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;

    // Contract path: GET /api/orders/:orderId/tracking
    @GetMapping("/api/orders/{orderNumber}/tracking")
    public ResponseEntity<Map<String, Object>> getTrackingContract(@PathVariable String orderNumber) {
        TrackingResponse res = trackingService.trackOrder(orderNumber);

        Map<String, Object> map = new HashMap<>();
        map.put("orderId", res.getOrderNumber());
        map.put("status", res.getOrderStatus().name());
        map.put("vehicleType", res.getVehicleType().name());
        map.put("currentLat", res.getCurrentLat());
        map.put("currentLng", res.getCurrentLng());
        map.put("estimatedArrival", LocalDateTime.now().plusMinutes(res.getEtaMinutesRemaining()).format(DateTimeFormatter.ISO_DATE_TIME));
        // Attach detailed progress fields
        map.put("progressPercent", res.getProgressPercent());
        map.put("currentStageDescription", res.getCurrentStageDescription());
        map.put("events", res.getEvents());

        return ResponseEntity.ok(map);
    }

    // Legacy path: GET /api/tracking/{orderNumber}
    @GetMapping("/api/tracking/{orderNumber}")
    public ResponseEntity<TrackingResponse> trackOrder(@PathVariable String orderNumber) {
        return ResponseEntity.ok(trackingService.trackOrder(orderNumber));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotFound(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}
