package com.wedelivery;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接入方向：站点与机器基础信息的批量幂等导入。写库，故整类回滚。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("接入 · 基础信息批量导入")
class StationVehicleImportApiTest {

    private static final String STATION_IMPORT = "/api/dispatch/stations/import";
    private static final String VEHICLE_IMPORT = "/api/dispatch/vehicles/import";

    private static final String NEW_STATION =
            "{\"id\":99,\"name\":\"Test Hub\",\"address\":\"1 Test Rd\",\"latitude\":37.1,"
                    + "\"longitude\":-122.1,\"totalDroneBays\":5,\"totalRobotBays\":5,"
                    + "\"contactPhone\":\"(415) 555-9999\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("站点导入：容量按坪位派生，字段完整落库")
    void stationImportStoresAllFields() throws Exception {
        mvc.perform(post(STATION_IMPORT).contentType(MediaType.APPLICATION_JSON).content("[" + NEW_STATION + "]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.errors", hasSize(0)));

        Map<String, Object> imported = listStations().stream()
                .filter(s -> "Test Hub".equals(s.get("name")))
                .findFirst()
                .orElseThrow(AssertionError::new);

        assertThat(imported.get("address")).isEqualTo("1 Test Rd");
        assertThat(imported.get("contactPhone")).isEqualTo("(415) 555-9999");
        assertThat(((Number) imported.get("maxCapacity")).intValue()).isEqualTo(10);
    }

    /**
     * 回归防护：Station.id 由调用方显式指定（而非数据库自增），导入必须遵守该编号。
     * 曾因 Station.id 用 GenerationType.IDENTITY，落库时 id 被丢弃、改由数据库自增
     * （`insert into stations (id, ...) values (default, ?)`），导致编号不被遵守且重复导入新增重复行。
     * 若有人把 @GeneratedValue 加回 Station.id，本用例会失败。
     */
    @Test
    @DisplayName("站点导入：保留显式给出的站点编号（编号即 id）")
    void stationImportKeepsExplicitId() throws Exception {
        mvc.perform(post(STATION_IMPORT).contentType(MediaType.APPLICATION_JSON).content("[" + NEW_STATION + "]"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/stations/99/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationId").value(99))
                .andExpect(jsonPath("$.maxCapacity").value(10));
    }

    /** 回归防护：同一编号重复导入必须走更新而非新增（依赖站点编号被遵守）。 */
    @Test
    @DisplayName("站点导入按编号幂等：第二次导入应记为 updated 而非再次 created")
    void stationImportIsIdempotent() throws Exception {
        mvc.perform(post(STATION_IMPORT).contentType(MediaType.APPLICATION_JSON).content("[" + NEW_STATION + "]"))
                .andExpect(jsonPath("$.created").value(1));

        mvc.perform(post(STATION_IMPORT).contentType(MediaType.APPLICATION_JSON).content("[" + NEW_STATION + "]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(1));
    }

    @Test
    @DisplayName("站点导入：非法行只记入 errors，不影响同一批中的合法行")
    void stationImportReportsBadRowsWithoutAborting() throws Exception {
        String body = "[{\"name\":\"No Id\",\"address\":\"x\",\"latitude\":1,\"longitude\":1},"
                + "{\"id\":98,\"name\":\"Good Hub\",\"address\":\"2 Test Rd\",\"latitude\":37.2,\"longitude\":-122.2}]";

        mvc.perform(post(STATION_IMPORT).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.errors", hasSize(1)));
    }

    @Test
    @DisplayName("机器导入：未给能力字段时按类型基线兜底，新机默认停在所属站点")
    void vehicleImportAppliesTypeDefaultsAndParksAtStation() throws Exception {
        String body = "[{\"vehicleCode\":\"TEST-V-01\",\"vehicleType\":\"DRONE\",\"stationId\":1}]";

        mvc.perform(post(VEHICLE_IMPORT).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.errors", hasSize(0)));

        mvc.perform(get("/api/vehicles/TEST-V-01/info"))
                .andExpect(status().isOk())
                // DRONE 类型基线：3kg / 0.05m³ / 45km/h / 30min
                .andExpect(jsonPath("$.maxWeight").value(3.0))
                .andExpect(jsonPath("$.maxVolume").value(0.05))
                .andExpect(jsonPath("$.cruiseSpeed").value(45.0))
                .andExpect(jsonPath("$.enduranceMinutes").value(30.0))
                .andExpect(jsonPath("$.maxDeliverableDistanceKm").value(22.5));

        mvc.perform(get("/api/dispatch/vehicles/TEST-V-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDLE"))
                .andExpect(jsonPath("$.locationCode").value(1));
    }

    @Test
    @DisplayName("机器导入按载具编号幂等")
    void vehicleImportIsIdempotent() throws Exception {
        String body = "[{\"vehicleCode\":\"TEST-V-02\",\"vehicleType\":\"ROBOT\",\"stationId\":2}]";

        mvc.perform(post(VEHICLE_IMPORT).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.created").value(1));
        mvc.perform(post(VEHICLE_IMPORT).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(1));
    }

    @Test
    @DisplayName("机器导入：短续航机器的可配送路程按「续航 ÷ 60 × 最大速度」派生")
    void shortEnduranceYieldsShortRange() throws Exception {
        String body = "[{\"vehicleCode\":\"TEST-V-SHORT\",\"vehicleType\":\"DRONE\",\"stationId\":1,"
                + "\"enduranceMinutes\":5}]";

        mvc.perform(post(VEHICLE_IMPORT).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        // 5 分钟 ÷ 60 × 45 km/h = 3.75 km，远低于满续航机型的 22.5 km
        mvc.perform(get("/api/vehicles/TEST-V-SHORT/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enduranceMinutes").value(5.0))
                .andExpect(jsonPath("$.maxDeliverableDistanceKm").value(3.75));
    }

    @Test
    @DisplayName("机器导入：所属站点不存在时该行被拒绝")
    void vehicleImportRejectsUnknownStation() throws Exception {
        String body = "[{\"vehicleCode\":\"TEST-V-BAD\",\"vehicleType\":\"DRONE\",\"stationId\":9999}]";

        mvc.perform(post(VEHICLE_IMPORT).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.errors", hasSize(1)));
    }

    private List<Map<String, Object>> listStations() throws Exception {
        String json = mvc.perform(get("/api/stations"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
        });
    }
}
