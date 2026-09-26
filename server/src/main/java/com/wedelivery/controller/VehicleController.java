package com.wedelivery.controller;

import com.wedelivery.dto.VehicleInfoDto;
import com.wedelivery.dto.VehicleRealtimeDto;
import com.wedelivery.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 机器（机器人 / 无人机）只读查询。
 *
 * - 基础信息接入订单系统：编号、类型、最大载荷（重量 / 体积）、默认最大速度、续航、最大可配送路程。
 * - 实时信息接入实时追踪系统：位置（经纬度 + 0/1/2/3 编码）、状态、当前速度、各类更新时间。
 */
@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    /** 全部机器的基础信息 */
    @GetMapping
    public ResponseEntity<List<VehicleInfoDto>> listVehicleInfo() {
        return ResponseEntity.ok(vehicleService.listVehicleInfo());
    }

    /** 单台机器的实时信息（实时追踪系统的数据源） */
    @GetMapping("/{code}")
    public ResponseEntity<VehicleRealtimeDto> getVehicleRealtime(@PathVariable("code") String code) {
        return ResponseEntity.ok(vehicleService.getVehicleRealtime(code));
    }

    /** 单台机器的基础信息 */
    @GetMapping("/{code}/info")
    public ResponseEntity<VehicleInfoDto> getVehicleInfo(@PathVariable("code") String code) {
        return ResponseEntity.ok(vehicleService.getVehicleInfo(code));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
}
