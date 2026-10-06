package com.wedelivery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wedelivery.entity.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {
    private String token;
    private UserDto user;
    
    // 兼容字段
    private Long id;
    private String username;
    private String email;
    private Role role;
    private LocalDateTime vipExpireAt;
    private String message;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserDto {
        private String id;
        private String username;
        private String email;
        private String role;
    }
}
