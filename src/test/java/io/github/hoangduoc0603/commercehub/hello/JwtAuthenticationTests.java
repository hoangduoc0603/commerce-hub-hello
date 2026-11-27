package io.github.hoangduoc0603.commercehub.hello;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// G0-29: mọi route bắt JWT của realm commerce-hub (keycloak.md mục "Resource server", BR-04 phần token).
// Bước 1 chỉ có case "không token". Token thật và token xấu ở bước 2, 4.
@SpringBootTest
// addFilters mặc định true: MockMvc chạy cả springSecurityFilterChain, đúng như Tomcat thật.
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JwtAuthenticationTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void BR04_readWithoutTokenIsUnauthorized() {
        // ID ngẫu nhiên: trước G0-29 trả 404. Sau G0-29, Security chặn trước khi tới controller.
        assertThat(mvc.get().uri("/v1/greetings/{id}", UUID.randomUUID()))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void BR04_createWithoutTokenIsUnauthorized() {
        // POST quan trọng hơn GET: không token mà ghi được thì ai cũng ghi được.
        // Nếu ra 403 thay vì 401 thì CSRF còn bật (xem SecurityConfig).
        assertThat(mvc.post().uri("/v1/greetings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Đức"}"""))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
