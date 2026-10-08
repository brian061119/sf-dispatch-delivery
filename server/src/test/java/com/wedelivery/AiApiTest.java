package com.wedelivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedelivery.service.GeminiDeliveryClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AiApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean GeminiDeliveryClient gemini;
    @Test void requiresLogin() throws Exception {
        mvc.perform(post("/api/ai/parse").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"send 2kg\"}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(gemini);
    }
    @Test @WithMockUser void parseReturnsWizardDraft() throws Exception {
        when(gemini.extract(anyString(), anyList())).thenReturn(mapper.readTree("{\"intent\":\"DRAFT\",\"weightKg\":2,\"priority\":\"EXPRESS\",\"pickupText\":\"Market St\"}"));
        mvc.perform(post("/api/ai/parse").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"send 2kg express\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.prefill.pkg.weightKg").value(2))
            .andExpect(jsonPath("$.prefill.priority").value("EXPRESS"))
            .andExpect(jsonPath("$.prefill.pickup.line1").value("Market St"));
    }
    @Test @WithMockUser void chatExplainsMissingLocations() throws Exception {
        when(gemini.extract(anyString(), anyList())).thenReturn(mapper.readTree("{\"intent\":\"QUOTE\",\"weightKg\":2}"));
        mvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"how much for 2kg?\",\"history\":[]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cards[0].type").value("prefill")).andExpect(jsonPath("$.reply").isString());
    }
    @Test @WithMockUser void providerFailureIsSanitizedAndActionable() throws Exception {
        when(gemini.extract(anyString(), anyList())).thenThrow(new IllegalStateException("Gemini quota exhausted."));
        mvc.perform(post("/api/ai/parse").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"send 2kg\"}"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("Gemini quota exhausted."));
    }
    @Test @WithMockUser void rejectsBlankRequests() throws Exception {
        mvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\" \"}"))
            .andExpect(status().isBadRequest());verifyNoInteractions(gemini);
    }
}
