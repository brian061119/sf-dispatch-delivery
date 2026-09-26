package com.wedelivery.dto;

import com.wedelivery.entity.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String password;

    // 注册使用可选字段
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
}
