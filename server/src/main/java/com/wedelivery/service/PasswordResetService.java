package com.wedelivery.service;

import com.wedelivery.entity.PasswordResetToken;
import com.wedelivery.entity.User;
import com.wedelivery.repository.PasswordResetTokenRepository;
import com.wedelivery.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Forgot-password flow: request a one-time link, then set a new password with it.
 *
 * There is no email service, so the link is written to the backend log where a real
 * app would email it. With wedelivery.auth.password-reset.expose-link=true
 * (DEMO_SHOW_RESET_LINK) it is also returned in the API response so the demo can
 * show it on the page. That setting lets anyone who knows a username reset that
 * account, so it is off by default and must stay off outside local demos.
 */
@Service
@Slf4j
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean exposeLink;
    private final String frontendBaseUrl;
    private final long ttlMinutes;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${wedelivery.auth.password-reset.expose-link:false}") boolean exposeLink,
                                @Value("${wedelivery.auth.password-reset.frontend-base-url:http://localhost:3000}") String frontendBaseUrl,
                                @Value("${wedelivery.auth.password-reset.ttl-minutes:30}") long ttlMinutes) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.exposeLink = exposeLink;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
        this.ttlMinutes = ttlMinutes;
    }

    /**
     * Creates a reset link for the account matching a username or email.
     * Callers must answer the same way whether or not an account exists.
     *
     * @return the link, only when expose-link is enabled and the account exists
     */
    @Transactional
    public Optional<String> requestReset(String identifier) {
        String id = identifier == null ? "" : identifier.trim();
        Optional<User> user = userRepository.findByUsername(id);
        if (!user.isPresent() && id.contains("@")) {
            user = userRepository.findByEmailIgnoreCase(id);
        }
        if (!user.isPresent()) {
            log.info("Password reset requested for unknown account '{}'", id);
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now();
        // Only the newest link works: retire any earlier unused ones.
        for (PasswordResetToken old : tokenRepository.findByUserIdAndUsedAtIsNull(user.get().getId())) {
            old.setUsedAt(now);
        }

        String rawToken = newToken();
        tokenRepository.save(PasswordResetToken.builder()
                .userId(user.get().getId())
                .tokenHash(sha256(rawToken))
                .expiresAt(now.plusMinutes(ttlMinutes))
                .build());

        String link = frontendBaseUrl + "/reset-password?token=" + rawToken;
        // Stand-in for sending an email.
        log.info("[PASSWORD RESET] Link for {} (valid {} min): {}", user.get().getUsername(), ttlMinutes, link);
        return exposeLink ? Optional.of(link) : Optional.empty();
    }

    /** Sets a new password if the link is valid, unused and not expired; the link then stops working. */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        LocalDateTime now = LocalDateTime.now();
        PasswordResetToken token = tokenRepository.findByTokenHash(sha256(rawToken == null ? "" : rawToken.trim()))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new IllegalArgumentException(
                        "This reset link is invalid or has expired. Please request a new one."));

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "This reset link is invalid or has expired. Please request a new one."));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.setUsedAt(now);
        log.info("Password reset completed for {}", user.getUsername());
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
