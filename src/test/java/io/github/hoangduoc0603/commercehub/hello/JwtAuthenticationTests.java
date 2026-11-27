package io.github.hoangduoc0603.commercehub.hello;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// G0-29: mọi route bắt JWT của realm commerce-hub (keycloak.md mục "Resource server", BR-04 phần token).
// 401 là Problem Details có traceId, WWW-Authenticate theo RFC 6750 (bước 3).
// Token ký thật bằng TestJwts, decoder thật của Boot tải JWKS giả (TestJwtConfiguration). Token xấu ở bước 4.
// 401 là Problem Details có traceId, WWW-Authenticate theo RFC 6750 §3 không kèm lý do (bước 3).
@SpringBootTest
// addFilters mặc định true: MockMvc chạy cả springSecurityFilterChain, đúng như Tomcat thật.
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestJwtConfiguration.class})
class JwtAuthenticationTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestJwts jwts;

    @Test
    void BR04_validTokenCanCreateGreeting() {
        // Token đi qua JwtDecoder thật của Boot: tải JWKS, chọn khóa theo kid, kiểm chữ ký, rồi iss, aud, exp, nbf, typ.
        assertThat(mvc.post().uri("/v1/greetings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwts.valid())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Đức"}"""))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void BR04_readWithoutTokenIsUnauthorized() {
        // ID ngẫu nhiên: trước G0-29 trả 404. Sau G0-29, Security chặn trước khi tới controller.
        MvcTestResult result = mvc.get().uri("/v1/greetings/{id}", UUID.randomUUID()).exchange();
        assertUnauthorizedProblem(result);
        // RFC 6750 §3: không có token thì không kèm mã lỗi.
        assertThat(result).hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }

    @Test
    void BR04_createWithoutTokenIsUnauthorized() {
        // POST quan trọng hơn GET: không token mà ghi được thì ai cũng ghi được.
        // Nếu ra 403 thay vì 401 thì CSRF còn bật (xem SecurityConfig).
        MvcTestResult result = mvc.post().uri("/v1/greetings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Đức"}""")
                .exchange();
        assertUnauthorizedProblem(result);
        assertThat(result).hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }

    @Test
    void BR04_malformedTokenIsUnauthorizedWithoutReason() {
        // Token hỏng đi đường BearerTokenAuthenticationFilter, khác đường "không token" (ExceptionTranslationFilter).
        MvcTestResult result = mvc.get().uri("/v1/greetings/{id}", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                .exchange();
        assertUnauthorizedProblem(result);
        // RFC 6750 §3.1: token có mà hỏng thì error="invalid_token". Không error_description: mặc định Spring ghi lý do
        // ("Malformed token", "The iss claim is not valid"), giúp người dò token biết đang sai chỗ nào.
        assertThat(result).hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
    }

    // Cùng dạng với assertGenericProblem của ProblemDetailsTests (rest-api.md mục Test): JsonPath trên đúng tên field.
    private static void assertUnauthorizedProblem(MvcTestResult result) {
        assertThat(result)
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().doesNotHavePath("$.type");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Unauthorized");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(401);
        assertThat(result).bodyJson().extractingPath("$.traceId").asString().matches("[0-9a-f]{32}");
    }
}
