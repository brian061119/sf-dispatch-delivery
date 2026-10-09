package com.wedelivery;

import com.wedelivery.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Default config (demo mode off): the reset link must never appear in the response. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Auth · forgot password does not expose the link by default")
class PasswordResetDefaultModeApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Test
    @DisplayName("Existing account: link is created (logged) but not returned")
    void linkIsNotReturned() throws Exception {
        long before = tokenRepository.count();
        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"normal_user\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.resetLink").doesNotExist());
        assertThat(tokenRepository.count()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("Blank identifier → 400")
    void blankIdentifierIs400() throws Exception {
        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }
}
