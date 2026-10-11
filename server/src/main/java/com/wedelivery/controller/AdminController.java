package com.wedelivery.controller;

import com.wedelivery.dto.AdminDashboardDto;
import com.wedelivery.dto.AdminUserDto;
import com.wedelivery.service.AdminUserService;
import com.wedelivery.service.StationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final StationService stationService;
    private final AdminUserService adminUserService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminDashboardDto> getDashboard() {
        return ResponseEntity.ok(stationService.getAdminDashboard());
    }

    /** Every user with order counts (read-only). */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminUserDto.Summary>> listUsers() {
        return ResponseEntity.ok(adminUserService.listUsers());
    }

    /** One user and all of their orders, newest first (read-only). 404 if the id is unknown. */
    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminUserDto.Detail> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(adminUserService.getUser(userId));
    }
}
