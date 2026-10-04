package com.wedelivery.controller;

import com.wedelivery.dto.OrderCreateRequest;
import com.wedelivery.dto.OrderCreateResponse;
import com.wedelivery.dto.OrderReviewRequest;
import com.wedelivery.dto.OrderReviewResponse;
import com.wedelivery.dto.OrderUpdateRequest;
import com.wedelivery.entity.Order;
import com.wedelivery.entity.User;
import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * API Contract: POST /api/orders (Create order and lock vehicle)
     */
    @PostMapping
    public ResponseEntity<OrderCreateResponse> createOrder(
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
            m.put("trackingCode", o.getTrackingCode());

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
            m.put("vehicleType", o.getVehicleType() != null ? o.getVehicleType().name() : null);
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
    public ResponseEntity<Order> getOrder(
            @PathVariable String orderNumber,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(orderService.getAccessibleOrder(orderNumber, currentUser));
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

    /**
     * API Contract: PATCH /api/orders/:orderId/cancel (Cancel order, release vehicle and refund payment)
     */
    @PatchMapping("/{orderNumber}/cancel")
    public ResponseEntity<Map<String, Object>> cancelOrder(
            @PathVariable String orderNumber,
            @AuthenticationPrincipal User currentUser
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Order order = orderService.cancelOrder(orderNumber, currentUser);

        Map<String, Object> res = new HashMap<>();
        res.put("orderId", order.getOrderNumber());
        res.put("status", "CANCELLED");
        res.put("cancellationFee", order.getCancellationFee());
        res.put("refundAmount", order.getRefundAmount());
        res.put("returnStationId", order.getReturnStationId());
        return ResponseEntity.ok(res);
    }

    /**
     * API Contract: PATCH /api/orders/:orderId (Update order details before transit)
     */
    @PatchMapping("/{orderNumber}")
    public ResponseEntity<Order> updateOrder(
            @PathVariable String orderNumber,
            @RequestBody OrderUpdateRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(orderService.updateOrder(orderNumber, request, currentUser));
    }

    /**
     * API Contract: POST /api/orders/:orderId/review (Submit delivery review)
     */
    @PostMapping("/{orderNumber}/review")
    public ResponseEntity<OrderReviewResponse> submitReview(
            @PathVariable String orderNumber,
            @Valid @RequestBody OrderReviewRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(orderService.submitReview(orderNumber, request, currentUser));
    }

    /**
     * API Contract: GET /api/orders/:orderId/review (Get delivery review)
     */
    @GetMapping("/{orderNumber}/review")
    public ResponseEntity<OrderReviewResponse> getReview(
            @PathVariable String orderNumber,
            @AuthenticationPrincipal User currentUser
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(orderService.getReview(orderNumber, currentUser));
    }
}
