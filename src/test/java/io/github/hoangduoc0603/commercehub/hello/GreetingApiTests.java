package io.github.hoangduoc0603.commercehub.hello;

import static org.assertj.core.api.Assertions.assertThat;
// jwt(): đặt sẵn JwtAuthenticationToken vào SecurityContext, KHÔNG qua JwtDecoder (G0-29). Kiểm token thật: JwtAuthenticationTests.
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// Seam test 2 của spec G0: service qua HTTP (MockMvc), DB thật qua PgBouncer, Flyway chạy từ đầu.
// JSON kiểm bằng JsonPath trên đúng tên field: đổi tên field trong DTO thì test đỏ (story 69, ADR-0019).
@SpringBootTest
// Boot 4: annotation nằm trong spring-boot-webmvc-test (package webmvc.test.autoconfigure), không còn ở
// test.autoconfigure.web.servlet như Boot 3. Có AssertJ trên classpath thì Boot tạo luôn bean MockMvcTester.
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GreetingApiTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void createdGreetingCanBeReadBack() {
        MvcTestResult created = mvc.post().uri("/v1/greetings").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                // Có dấu tiếng Việt: kiểm UTF-8 đi suốt từ JSON xuống DB rồi về.
                .content("""
                        {"name": "Đức"}""")
                .exchange();

        assertThat(created).hasStatus(HttpStatus.CREATED);
        // Location tương đối (RFC 9110 cho phép), xem comment ở controller.
        String location = created.getResponse().getHeader("Location");
        assertThat(location).matches("/v1/greetings/[0-9a-f-]{36}");
        String id = location.substring("/v1/greetings/".length());

        assertThat(created).bodyJson().extractingPath("$.id").isEqualTo(id);
        assertThat(created).bodyJson().extractingPath("$.name").isEqualTo("Đức");
        // ISO-8601 UTC, ví dụ 2026-11-25T01:20:33.123456Z: mặc định của Jackson 3 cho Instant.
        assertThat(created).bodyJson().extractingPath("$.createdAt").asString()
                .matches("\\d{4}-\\d{2}-\\d{2}T[0-9:.]+Z");

        // Đọc lại đúng địa chỉ trong Location. isLenientlyEqualTo: chỉ so các field liệt kê, bỏ qua createdAt.
        assertThat(mvc.get().uri(location).with(jwt()))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"id": "%s", "name": "Đức"}""".formatted(id));
    }
}