package com.wedelivery.dto;

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
    // 注意: 不接受 role 字段 —— 注册一律为普通用户，请求中携带的 role 会被忽略。
    // ADMIN / VIP 只能由开发者在数据库中直接设置。
}
