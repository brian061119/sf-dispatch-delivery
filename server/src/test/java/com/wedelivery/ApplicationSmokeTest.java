package com.wedelivery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 启动冒烟：在真实随机端口拉起内嵌容器，确认服务能对外提供 HTTP 服务。
 *
 * 与其余用 MockMvc 的用例不同，本类会真正绑定端口，是「项目能否正常运行」最直接的证据：
 * 只要 Spring 上下文或 Web 层装配有问题，这里就会在启动阶段失败。
 * 全部为只读调用，因此不需要事务回滚。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("启动冒烟测试")
class ApplicationSmokeTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("内嵌容器启动后，站点与机器接口均返回 200")
    void serviceStartsAndServesHttp() {
        assertOk("/api/stations", "Station 1");
        assertOk("/api/vehicles", "DRONE-DT-01");
        assertOk("/api/stations/1/availability", "maxCapacity");
        assertOk("/api/dispatch/stations/realtime", "maxCapacity");
        assertOk("/api/dispatch/vehicles", "ROBOT-SS-02");
    }

    @Test
    @DisplayName("种子数据已加载：3 个站点、15 台机器")
    void seedDataIsLoaded() {
        String stations = rest.getForObject("/api/stations", String.class);
        String vehicles = rest.getForObject("/api/vehicles", String.class);

        assertThat(stations).contains("Station 2 - Sunset District Hub");
        assertThat(stations).contains("Station 3 - Mission District Hub");
        assertThat(vehicles).contains("ROBOT-DT-04");
    }

    @Test
    @DisplayName("未知机器编号返回 404 而非 500")
    void unknownVehicleReturnsNotFound() {
        ResponseEntity<String> res = rest.getForEntity("/api/vehicles/NO-SUCH-VEHICLE", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private void assertOk(String path, String asciiMarker) {
        ResponseEntity<String> res = rest.getForEntity(path, String.class);
        assertThat(res.getStatusCode()).as("GET %s", path).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).as("GET %s 响应体", path).contains(asciiMarker);
    }
}
