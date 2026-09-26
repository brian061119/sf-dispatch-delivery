package com.wedelivery.controller;

import com.wedelivery.dto.OrderCreateRequest;
import com.wedelivery.dto.OrderCreateResponse;
import com.wedelivery.entity.Order;
import com.wedelivery.entity.User;
import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.entity.enums.Role;
import com.wedelivery.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * API Contract: POST /api/orders
     * Accepts candidateId and route specs, performs server-side price validation,
     * atomically locks vehicle, processes mock payment, and creates order.
     */
    @PostMapping
    public ResponseEntity<OrderCreateResponse> createOrderContract(
            @Valid @RequestBody OrderCreateRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        OrderCreateResponse response = orderService.createOrder(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * API Contract: GET /api/orders (Get current user's order list)
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getOrdersContract(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Order> orders = orderService.getUserOrders(currentUser.getId());

        List<Map<String, Object>> orderList = orders.stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("orderId", o.getOrderNumber());

            // External contract status mapping: PENDING | IN_TRANSIT | DELIVERED | CANCELLED
            String contractStatus;
            if (o.getStatus() == OrderStatus.DELIVERED) {
                contractStatus = "DELIVERED";
            } else if (o.getStatus() == OrderStatus.CANCELLED) {
                contractStatus = "CANCELLED";
            } else if (o.getStatus() == OrderStatus.IN_TRANSIT) {
                contractStatus = "IN_TRANSIT";
            } else {
                contractStatus = "PENDING";
            }
            m.put("status", contractStatus);
            m.put("detailStatus", o.getStatus().name());
            m.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().format(DateTimeFormatter.ISO_DATE_TIME) : "");
            m.put("packageDescription", o.getPickupAddress() + " -> " + o.getDropoffAddress());
            m.put("estimatedCost", o.getFinalPrice());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> res = new HashMap<>();
        res.put("orders", orderList);
        return ResponseEntity.ok(res);
    }

    /**
     * API Contract: GET /api/orders/:orderId (Get order detail)
     */
    @GetMapping("/{orderNumber}")
    public ResponseEntity<Map<String, Object>> getOrder(
            @PathVariable String orderNumber,
            @AuthenticationPrincipal User currentUser
    ) {
        Order order = orderService.getOrderByNumber(orderNumber);

        // Authorization check: only order owner or ADMIN allowed
        if (currentUser != null && currentUser.getRole() != Role.ADMIN && !order.getUserId().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Map<String, Object> map = new HashMap<>();
        map.put("orderId", order.getOrderNumber());
        map.put("status", order.getStatus().name());
        map.put("planType", order.getPlanType().name());
        map.put("vehicleType", order.getVehicleType().name());
        map.put("stationId", order.getStationId());
        map.put("pickupAddress", order.getPickupAddress());
        map.put("pickupLat", order.getPickupLat());
        map.put("pickupLng", order.getPickupLng());
        map.put("dropoffAddress", order.getDropoffAddress());
        map.put("dropoffLat", order.getDropoffLat());
        map.put("dropoffLng", order.getDropoffLng());
        map.put("packageWeight", order.getPackageWeight());
        map.put("packageVolume", order.getPackageVolume());
        map.put("totalDistance", order.getTotalDistance());
        map.put("originPrice", order.getOriginPrice());
        map.put("discountAmount", order.getDiscountAmount());
        map.put("finalPrice", order.getFinalPrice());
        map.put("scheduledStartTime", order.getScheduledStartTime());
        map.put("estimatedDeliveryTime", order.getEstimatedDeliveryTime());
        map.put("actualDeliveryTime", order.getActualDeliveryTime());
        map.put("createdAt", order.getCreatedAt());

        return ResponseEntity.ok(map);
    }

    /**
     * API Contract: PATCH /api/orders/:orderId/confirm-receipt (Confirm receipt and release vehicle)
     */
    @PatchMapping("/{orderNumber}/confirm-receipt")
    public ResponseEntity<Map<String, Object>> confirmReceipt(
            @PathVariable String orderNumber,
            @AuthenticationPrincipal User currentUser
    ) {
        Order order = orderService.confirmReceipt(orderNumber, currentUser);

        Map<String, Object> res = new HashMap<>();
        res.put("orderId", order.getOrderNumber());
        res.put("status", "DELIVERED");
        return ResponseEntity.ok(res);
    }
}
