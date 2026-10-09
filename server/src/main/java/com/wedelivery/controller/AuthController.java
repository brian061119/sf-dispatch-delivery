package com.wedelivery.controller;

import com.wedelivery.dto.AuthRequest;
import com.wedelivery.dto.AuthResponse;
import com.wedelivery.entity.User;
import com.wedelivery.service.PasswordResetService;
import com.wedelivery.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(userService.authenticate(request));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(userService.register(request));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse.UserDto> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        // Return user info including VIP status and expiration
        return ResponseEntity.ok(AuthResponse.UserDto.builder()
                .id(String.valueOf(user.getId()))
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .isVip(user.isVip())
                .vipExpireAt(user.getVipExpireAt())
                .build());
    }

    /**
     * Forgot password: always 200 with the same message, whether or not the account
     * exists, so the endpoint can't be used to discover accounts. resetLink is only
     * included in demo mode (DEMO_SHOW_RESET_LINK=true); otherwise see the backend log.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message", "If an account matches, a password reset link has been sent. It expires in 30 minutes.");
        passwordResetService.requestReset(request.getIdentifier()).ifPresent(link -> res.put("resetLink", link));
        return ResponseEntity.ok(res);
    }

    /** Sets a new password with a reset link token. Invalid, used or expired token → 400. */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message", "Your password has been reset. You can now log in with your new password.");
        return ResponseEntity.ok(res);
    }

    @Data
    public static class ForgotPasswordRequest {
        /** Username or email address. */
        @NotBlank(message = "Enter your username or email")
        @Size(max = 128)
        private String identifier;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank(message = "Reset token is missing")
        private String token;
        @NotBlank(message = "Enter a new password")
        @Size(min = 6, max = 128, message = "Password must be 6 to 128 characters")
        private String newPassword;
    }
}
