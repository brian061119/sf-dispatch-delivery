package com.wedelivery.controller;

import com.wedelivery.dto.VipStatusResponse;
import com.wedelivery.dto.VipSubscribeRequest;
import com.wedelivery.entity.User;
import com.wedelivery.service.VipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/vip")
@RequiredArgsConstructor
public class VipController {

    private final VipService vipService;

    @GetMapping("/status")
    public ResponseEntity<VipStatusResponse> getVipStatus(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(vipService.getVipStatus(currentUser));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<VipStatusResponse> subscribe(
            @Valid @RequestBody VipSubscribeRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(vipService.subscribe(currentUser, request));
    }
}
