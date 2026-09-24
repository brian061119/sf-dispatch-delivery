package com.wedelivery.controller;

import com.wedelivery.dto.QuoteRequest;
import com.wedelivery.dto.QuoteResponse;
import com.wedelivery.dto.RecommendationContractDto;
import com.wedelivery.entity.Station;
import com.wedelivery.entity.User;
import com.wedelivery.service.RecommendationService;
import com.wedelivery.service.StationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class DispatchController {

    private final RecommendationService recommendationService;
    private final StationService stationService;

    // 契约路径: POST /api/recommendations
    @PostMapping("/api/recommendations")
    public ResponseEntity<RecommendationContractDto.Response> getRecommendations(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal User currentUser
    ) {
        RecommendationContractDto.Response res = recommendationService.generateContractRecommendations(body, currentUser);
        return ResponseEntity.ok(res);
    }

    // 保持向下兼容: POST /api/dispatch/quote
    @PostMapping("/api/dispatch/quote")
    public ResponseEntity<QuoteResponse> getQuote(
            @Valid @RequestBody QuoteRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        QuoteResponse response = recommendationService.generateRecommendations(request, currentUser);
        return ResponseEntity.ok(response);
    }

    // 契约路径: GET /api/stations
    @GetMapping({"/api/stations", "/api/dispatch/stations"})
    public ResponseEntity<List<Station>> getStations() {
        return ResponseEntity.ok(stationService.getAllStations());
    }
}
