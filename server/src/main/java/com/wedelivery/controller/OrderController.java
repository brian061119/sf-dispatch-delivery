package com.wedelivery.controller;

import com.wedelivery.dto.CheckoutRequest;
import com.wedelivery.dto.CheckoutResponse;
import com.wedelivery.dto.PlanOptionDto;
import com.wedelivery.dto.QuoteRequest;
import com.wedelivery.entity.Order;
import com.wedelivery.entity.User;
import com.wedelivery.service.OrderService;
import com.wedelivery.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final RecommendationService recommendationService;

    // 契约路径: POST /api/orders
    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrderContract(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal User currentUser
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String candidateId = (String) body.getOrDefault("candidateId", "CAND-BEST_VALUE");

        // 服务端按同一份请求重新计算推荐并取用户所选方案：扣款价格、站点、载具类型与时间
        // 均与推荐结果一致，不信任客户端传入的价格。方案已不可用时返回 409。
        RecommendationService.SelectedPlan selected = recommendationService
                .resolveContractCandidate(body, currentUser, candidateId)
                .orElseThrow(() -> new IllegalStateException(
                        "Selected plan " + candidateId + " is no longer available. Please refresh the recommendations."));
        QuoteRequest quote = selected.getRequest();
        PlanOptionDto plan = selected.getPlan();

        CheckoutRequest req = CheckoutRequest.builder()
                .planType(plan.getPlanType())
                .vehicleType(plan.getVehicleType())
                .stationId(plan.getStationId())
                .pickupAddress(quote.getPickupAddress())
                .pickupLat(quote.getPickupLat())
                .pickupLng(quote.getPickupLng())
                .dropoffAddress(quote.getDropoffAddress())
                .dropoffLat(quote.getDropoffLat())
                .dropoffLng(quote.getDropoffLng())
                .packageWeight(quote.getPackageWeight())
                .packageVolume(quote.getPackageVolume())
                .totalDistance(plan.getTotalDistance())
                .originPrice(plan.getOriginPrice())
                .discountAmount(plan.getDiscountAmount())
                .finalPrice(plan.getFinalPrice())
                .scheduledStartTime(plan.getScheduledStartTime())
                .estimatedDeliveryTime(plan.getEstimatedDeliveryTime())
                .cardNumber("4532 8901 2345 6789") // 模拟支付卡号
                .build();

        CheckoutResponse response = orderService.checkoutAndLockVehicle(req, currentUser);

        Map<String, Object> res = new HashMap<>();
        res.put("orderId", response.getOrderNumber());
        res.put("trackingCode", response.getTrackingCode());
        res.put("status", "PENDING");
        res.put("estimatedTimeMinutes", plan.getEstimatedMinutes());
        res.put("estimatedCost", plan.getFinalPrice());

        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    // 契约路径: GET /api/orders (获取订单列表)
    @GetMapping
    public ResponseEntity<Map<String, Object>> getOrdersContract(@AuthenticationPrincipal User currentUser) {
        List<Order> orders;
        if (currentUser != null) {
            orders = orderService.getUserOrders(currentUser.getId());
        } else {
            orders = Collections.emptyList();
        }

        List<Map<String, Object>> orderList = orders.stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("orderId", o.getOrderNumber());
            m.put("trackingCode", o.getTrackingCode());
            m.put("status", o.getStatus().name());
            m.put("createdAt", o.getCreatedAt().format(DateTimeFormatter.ISO_DATE_TIME));
            m.put("packageDescription", o.getPickupAddress() + " -> " + o.getDropoffAddress());
            m.put("estimatedCost", o.getFinalPrice());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> res = new HashMap<>();
        res.put("orders", orderList);
        return ResponseEntity.ok(res);
    }

    // 契约路径: PATCH /api/orders/:orderId/confirm-receipt (确认签收)
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

    @GetMapping("/{orderNumber}")
    public ResponseEntity<Order> getOrder(
            @PathVariable String orderNumber,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(orderService.getAccessibleOrder(orderNumber, currentUser));
    }

    @GetMapping("/my")
    public ResponseEntity<List<Order>> getMyOrders(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(orderService.getUserOrders(currentUser.getId()));
    }
}
