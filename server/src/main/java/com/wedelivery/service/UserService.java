package com.wedelivery.service;

import com.wedelivery.dto.AuthRequest;
import com.wedelivery.dto.AuthResponse;
import com.wedelivery.entity.User;
import com.wedelivery.entity.enums.Role;
import com.wedelivery.repository.UserRepository;
import com.wedelivery.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public AuthResponse authenticate(AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.getUsername()));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid password.");
        }

        String token = jwtUtils.generateToken(user.getUsername(), user.getRole().name());

        AuthResponse.UserDto userDto = AuthResponse.UserDto.builder()
                .id(String.valueOf(user.getId()))
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();

        return AuthResponse.builder()
                .token(token)
                .user(userDto)
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .vipExpireAt(user.getVipExpireAt())
                .message("Login successful.")
                .build();
    }

    public AuthResponse register(AuthRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already taken: " + request.getUsername());
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }

        Role assignedRole = request.getRole() != null ? request.getRole() : Role.USER;
        LocalDateTime vipExpire = assignedRole == Role.VIP ? LocalDateTime.now().plusYears(1) : null;

        User user = User.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail() != null ? request.getEmail() : request.getUsername() + "@wedelivery.local")
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(assignedRole)
                .vipExpireAt(vipExpire)
                .build();

        user = userRepository.save(user);

        String token = jwtUtils.generateToken(user.getUsername(), user.getRole().name());

        AuthResponse.UserDto userDto = AuthResponse.UserDto.builder()
                .id(String.valueOf(user.getId()))
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();

        return AuthResponse.builder()
                .token(token)
                .user(userDto)
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .vipExpireAt(user.getVipExpireAt())
                .message("Registration successful.")
                .build();
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }
}
