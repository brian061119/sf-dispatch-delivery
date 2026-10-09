package com.wedelivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedelivery.entity.PasswordResetToken;
import com.wedelivery.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Forgot / reset password with demo mode on (link returned in the response). Rolls back. */
@SpringBootTest(properties = "wedelivery.auth.password-reset.expose-link=true")
@AutoConfigureMockMvc
@Transactional
@DisplayName("Auth · forgot and reset password (demo mode)")
class PasswordResetApiTest {

    private static final String GENERIC = "If an account matches";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Test
    @DisplayName("Reset by username: new password works, old one doesn't, link is single-use")
    void resetByUsername() throws Exception {
        String token = requestToken("normal_user");

        reset(token, "brandNew99").andExpect(status().isOk());

        login("normal_user", "brandNew99").andExpect(status().isOk());
        login("normal_user", "password123").andExpect(status().isUnauthorized());
        reset(token, "another123").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("invalid or has expired")));
    }

    @Test
    @DisplayName("Reset by email works, case-insensitively")
    void resetByEmail() throws Exception {
        String token = requestToken("VIP@gmail.com");
        reset(token, "vipPass42").andExpect(status().isOk());
        login("vip_user", "vipPass42").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Unknown account gets the same 200 message and no link")
    void unknownAccountLooksTheSame() throws Exception {
        forgot("nobody_here")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith(GENERIC)))
                .andExpect(jsonPath("$.resetLink").doesNotExist());
    }

    @Test
    @DisplayName("Requesting a new link retires the previous one")
    void newerLinkReplacesOlder() throws Exception {
        String first = requestToken("normal_user");
        String second = requestToken("normal_user");
        reset(first, "firstTry1").andExpect(status().isBadRequest());
        reset(second, "secondTry2").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Expired link, unknown token and too-short password are rejected with 400")
    void rejectsBadResets() throws Exception {
        String token = requestToken("normal_user");
        reset(token, "abc").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("6 to 128")));

        for (PasswordResetToken t : tokenRepository.findAll()) {
            t.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        }
        reset(token, "validPass1").andExpect(status().isBadRequest());
        reset("not-a-real-token", "validPass1").andExpect(status().isBadRequest());
        login("normal_user", "password123").andExpect(status().isOk());
    }

    private String requestToken(String identifier) throws Exception {
        String json = forgot(identifier)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith(GENERIC)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String link = objectMapper.readTree(json).path("resetLink").asText();
        assertThat(link).startsWith("http://localhost:3000/reset-password?token=");
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    private ResultActions forgot(String identifier) throws Exception {
        return mvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Collections.singletonMap("identifier", identifier))));
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        JsonNode body = objectMapper.createObjectNode().put("token", token).put("newPassword", newPassword);
        return mvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }
}
