package com.wedelivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin console user list and read-only user view. Writes orders, so the class rolls back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Admin · user list and user view")
class AdminUsersApiTest {

    private static final String NEW_ORDER = "{"
            + "\"candidateId\":\"CAND-BEST_VALUE\","
            + "\"pickup\":{\"line1\":\"Downtown\",\"lat\":37.7891720,\"lng\":-122.3970420},"
            + "\"dropoff\":{\"line1\":\"Mission\",\"lat\":37.7596000,\"lng\":-122.4269000},"
            + "\"package\":{\"description\":\"books\",\"weightKg\":1.5},"
            + "\"priority\":\"STANDARD\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Only admins can list users: no token → 401, customer → 403")
    void userListIsAdminOnly() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + login("normal_user")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users/1").header("Authorization", "Bearer " + login("vip_user")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("User list shows every user with order counts and no password hash")
    void listsUsersWithOrderCounts() throws Exception {
        String customer = login("normal_user");
        String orderNumber = createOrder(customer);

        String json = getAsAdmin("/api/admin/users");
        assertThat(json).doesNotContain("passwordHash").doesNotContain("$2a$");
        JsonNode users = objectMapper.readTree(json);
        assertThat(users.size()).isGreaterThanOrEqualTo(3);

        JsonNode normal = findUser(users, "normal_user");
        assertThat(normal.path("orderCount").asLong()).isEqualTo(1);
        assertThat(normal.path("activeOrderCount").asLong()).isEqualTo(1);
        assertThat(normal.path("role").asText()).isEqualTo("USER");
        assertThat(findUser(users, "vip_user").path("isVip").asBoolean()).isTrue();
        assertThat(findUser(users, "admin").path("orderCount").asLong()).isZero();

        // The view of that user lists the order just placed.
        JsonNode detail = objectMapper.readTree(getAsAdmin("/api/admin/users/" + normal.path("id").asLong()));
        assertThat(detail.path("user").path("username").asText()).isEqualTo("normal_user");
        assertThat(detail.path("orders").size()).isEqualTo(1);
        JsonNode order = detail.path("orders").get(0);
        assertThat(order.path("orderNumber").asText()).isEqualTo(orderNumber);
        assertThat(order.path("status").asText()).isEqualTo("PENDING");
        assertThat(order.path("detailStatus").asText()).isEqualTo("PAID");
        assertThat(order.path("finalPrice").decimalValue()).isPositive();
    }

    @Test
    @DisplayName("Unknown user id → 404")
    void unknownUserIs404() throws Exception {
        mvc.perform(get("/api/admin/users/999999").header("Authorization", "Bearer " + login("admin")))
                .andExpect(status().isNotFound());
    }

    private JsonNode findUser(JsonNode users, String username) {
        for (JsonNode u : users) {
            if (username.equals(u.path("username").asText())) {
                return u;
            }
        }
        throw new AssertionError("user not in list: " + username);
    }

    private String getAsAdmin(String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + login("admin")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String createOrder(String token) throws Exception {
        String json = mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_ORDER))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json).path("orderId").asText();
    }

    private String login(String username) throws Exception {
        String json = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json).path("token").asText();
    }
}
