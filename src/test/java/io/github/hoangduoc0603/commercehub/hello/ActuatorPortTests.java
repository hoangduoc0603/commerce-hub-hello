package io.github.hoangduoc0603.commercehub.hello;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.RestTestClient;

// RANDOM_PORT: chạy Tomcat thật. Khi management.server.port khác cổng chính, Spring Boot test tự đổi
// cả cổng quản trị sang cổng ngẫu nhiên, nên test không đụng app đang bootRun ở 8080/8081.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestJwtConfiguration.class})
class ActuatorPortTests {

    @LocalServerPort       // cổng chính (vai trò 8080)
    int serverPort;

    @LocalManagementPort   // cổng Actuator (vai trò 8081). Chưa tách cổng thì giá trị bằng serverPort
    int managementPort;

    @Autowired
    TestJwts jwts;

    // RestTestClient (Spring Framework 7, có sẵn trong spring-test) gọi HTTP thật, có sẵn
    // assertion cho status và JSON. Không cần thêm dependency.
    RestTestClient on(int port) {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void livenessAndReadinessAreOnMainPort() {
        // Probe của K8s (ticket 21, 24) sẽ gọi hai đường này ở cổng chính.
        on(serverPort).get().uri("/livez").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
        on(serverPort).get().uri("/readyz").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void actuatorIsNotOnMainPort() {
        // Cổng chính sẽ mở ra ngoài qua Gateway edge, nên không được có /actuator. Gửi token hợp lệ (G0-29): không token
        // thì Security trả 401 trước khi tới định tuyến, test không phân biệt được "không có" với "bị chặn".
        on(serverPort).get().uri("/actuator/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwts.valid())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void actuatorIsOnManagementPort() {
        on(managementPort).get().uri("/actuator/health").exchange()
                .expectStatus().isOk();
    }
}