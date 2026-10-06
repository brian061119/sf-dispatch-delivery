package com.wedelivery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 调动模块写接口的访问控制：未登录 401、普通用户 403、管理员放行；只读查询保持公开。
 * 写库，故整类回滚。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("调动模块 · 写接口访问控制")
class DispatchWriteAccessApiTest {

    private static final String BODY = "{}";

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/dispatch/stations/import",
            "/api/dispatch/vehicles/import",
            "/api/dispatch/vehicles/DRONE-DT-01/telemetry",
            "/api/dispatch/vehicles/DRONE-DT-01/location",
            "/api/dispatch/simulate/tick"
    })
    @DisplayName("未登录调用写接口返回 401")
    void writeRequiresLogin(String path) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/dispatch/stations/import",
            "/api/dispatch/vehicles/import",
            "/api/dispatch/vehicles/DRONE-DT-01/telemetry",
            "/api/dispatch/vehicles/DRONE-DT-01/location",
            "/api/dispatch/simulate/tick"
    })
    @WithMockUser(roles = "USER")
    @DisplayName("普通用户调用写接口返回 403")
    void writeForbiddenForCustomers(String path) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("管理员可调用写接口")
    void adminCanWrite() throws Exception {
        mvc.perform(post("/api/dispatch/simulate/tick"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/dispatch/stations", "/api/dispatch/vehicles", "/api/stations/1/availability", "/api/vehicles"})
    @DisplayName("只读查询无需登录")
    void readsArePublic(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("报价接口无需登录")
    void quoteIsPublic() throws Exception {
        int code = mvc.perform(post("/api/dispatch/quote").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andReturn().getResponse().getStatus();
        assertThat(code).isNotIn(401, 403);
    }
}
