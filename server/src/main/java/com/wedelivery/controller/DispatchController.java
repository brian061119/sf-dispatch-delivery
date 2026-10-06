package com.wedelivery.controller;

import com.wedelivery.dto.ImportResultDto;
import com.wedelivery.dto.QuoteRequest;
import com.wedelivery.dto.QuoteResponse;
import com.wedelivery.dto.RecommendationContractDto;
import com.wedelivery.dto.SimulationTickResponse;
import com.wedelivery.dto.StationAvailabilityDto;
import com.wedelivery.dto.StationInfoDto;
import com.wedelivery.dto.StationUpsertRequest;
import com.wedelivery.dto.VehicleLocationRequest;
import com.wedelivery.dto.VehicleRealtimeDto;
import com.wedelivery.dto.VehicleTelemetryRequest;
import com.wedelivery.dto.VehicleUpsertRequest;
import com.wedelivery.entity.User;
import com.wedelivery.service.RecommendationService;
import com.wedelivery.service.SimulationService;
import com.wedelivery.service.StationService;
import com.wedelivery.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 机器人与无人机调动模块的对外入口。
 *
 * 路径前缀 /api/dispatch 已在 SecurityConfig 放行，供「机器接入 / 地图接入 / 基础信息导入」这类
 * 设备与上游系统的机器对机器调用；站点与载具的只读查询另见 StationController / VehicleController。
 */
@RestController
@RequiredArgsConstructor
public class DispatchController {

    private final RecommendationService recommendationService;
    private final StationService stationService;
    private final VehicleService vehicleService;
    private final SimulationService simulationService;

    // ==========================================================
    // 智能调动：根据用户需求推荐方案
    // ==========================================================

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

    // ==========================================================
    // 站点：基础信息（需求 1）与实时信息（需求 2）
    // ==========================================================

    @GetMapping({"/api/stations", "/api/dispatch/stations"})
    public ResponseEntity<List<StationInfoDto>> getStations() {
        return ResponseEntity.ok(stationService.listStationInfo());
    }

    @GetMapping({"/api/stations/{id}/availability", "/api/dispatch/stations/{id}/availability"})
    public ResponseEntity<StationAvailabilityDto> getStationAvailability(@PathVariable("id") Long id) {
        return ResponseEntity.ok(stationService.getStationAvailability(id));
    }

    @GetMapping("/api/dispatch/stations/realtime")
    public ResponseEntity<List<StationAvailabilityDto>> listStationAvailability() {
        return ResponseEntity.ok(stationService.listStationAvailability());
    }

    /** 站点基础信息批量导入（按站点编号幂等 upsert） */
    @PostMapping("/api/dispatch/stations/import")
    public ResponseEntity<ImportResultDto> importStations(@RequestBody List<StationUpsertRequest> requests) {
        return ResponseEntity.ok(stationService.importStations(requests));
    }

    // ==========================================================
    // 机器：实时信息查询（需求 4）
    // ==========================================================

    @GetMapping("/api/dispatch/vehicles")
    public ResponseEntity<List<VehicleRealtimeDto>> listVehiclesRealtime() {
        return ResponseEntity.ok(vehicleService.listVehicleRealtime());
    }

    @GetMapping("/api/dispatch/vehicles/{code}")
    public ResponseEntity<VehicleRealtimeDto> getVehicleRealtime(@PathVariable("code") String code) {
        return ResponseEntity.ok(vehicleService.getVehicleRealtime(code));
    }

    // ==========================================================
    // 接入：机器心跳 / 地图位置
    // ==========================================================

    /** 机器接入：上报状态、电量、当前速度、续航时间 */
    @PostMapping("/api/dispatch/vehicles/{code}/telemetry")
    public ResponseEntity<VehicleRealtimeDto> reportTelemetry(
            @PathVariable("code") String code,
            @Valid @RequestBody VehicleTelemetryRequest request
    ) {
        return ResponseEntity.ok(vehicleService.applyTelemetry(code, request));
    }

    /** 地图接入：上报实时经纬度，位置编码由后端推导 */
    @PostMapping("/api/dispatch/vehicles/{code}/location")
    public ResponseEntity<VehicleRealtimeDto> reportLocation(
            @PathVariable("code") String code,
            @Valid @RequestBody VehicleLocationRequest request
    ) {
        return ResponseEntity.ok(vehicleService.applyLocation(code, request));
    }

    /** 机器基础信息批量导入（按载具编号幂等 upsert） */
    @PostMapping("/api/dispatch/vehicles/import")
    public ResponseEntity<ImportResultDto> importVehicles(@RequestBody List<VehicleUpsertRequest> requests) {
        return ResponseEntity.ok(vehicleService.importVehicles(requests));
    }

    // ==========================================================
    // demo 辅助：代替真实机器/地图心跳推进实时信息
    // ==========================================================

    @PostMapping("/api/dispatch/simulate/tick")
    public ResponseEntity<SimulationTickResponse> simulateTick() {
        return ResponseEntity.ok(simulationService.tick());
    }

    // ==========================================================
    // 异常映射：机器 / 站点不存在时返回 404 而非 500
    // ==========================================================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
}
