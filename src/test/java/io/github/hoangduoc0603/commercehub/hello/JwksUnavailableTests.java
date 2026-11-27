package io.github.hoangduoc0603.commercehub.hello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

import javax.net.ssl.SSLHandshakeException;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.JwkSetUriJwtDecoderBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

// G0-29 bước 6b: không kiểm được token vì lỗi phía server (không tải được JWKS) thì 503, không phải 401.
// RANDOM_PORT bắt buộc: lỗi 401 sai chỉ xuất hiện khi Tomcat thật forward exception tới /error (ERROR dispatch);
// MockMvc không có bước đó, exception ném thẳng ra test.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// KHÔNG import TestJwtConfiguration: class lồng dưới đây thay JWKS giả "chạy được" bằng JWKS giả "hỏng".
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class JwksUnavailableTests {

    @LocalServerPort
    int port;

    @Autowired
    TestJwts jwts;

    @Test
    void BR04_jwksUnavailableIsServiceUnavailable(CapturedOutput output) {
        ResponseEntity<String> response = RestClient.create("http://localhost:" + port)
                // id bất kỳ: request bị chặn ở filter, không tới controller.
                .get().uri("/v1/greetings/018f0000-0000-7000-8000-000000000000")
                // Token hợp lệ: lỗi phải đến từ việc không tải được khóa, không phải từ token.
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwts.valid())
                .retrieve()
                // Mặc định RestClient ném exception với 4xx/5xx; ta cần đọc response nên bỏ qua mọi status.
                .onStatus(status -> true, (request, res) -> { })
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getHeaders().getContentType()).hasToString("application/problem+json");
        // Không mời client gửi lại token: lỗi không phải của client.
        assertThat(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        // Chi tiết hạ tầng (lỗi TLS, địa chỉ Keycloak) không ra tới client.
        String body = response.getBody();
        assertThat(body).doesNotContain("PKIX").doesNotContain("auth.test");
        // traceId trong body tìm được dòng ERROR có nguyên nhân thật.
        String traceId = JsonPath.read(body, "$.traceId");
        assertThat(traceId).matches("[0-9a-f]{32}");
        assertThat(output).contains("ERROR").contains(traceId).contains("PKIX path building failed (test)");
    }

    // Class lồng @TestConfiguration được nạp thêm vào context của test này.
    @TestConfiguration(proxyBeanMethods = false)
    static class UnreachableJwks {

        // Vẫn cần TestJwts để ký token hợp lệ; khóa của nó không bao giờ được tải về, đúng ý đồ.
        @Bean
        TestJwts testJwts(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
            return new TestJwts(issuer);
        }

        // Cùng cơ chế với TestJwtConfiguration, nhưng GET JWKS ném lỗi TLS như khi pod không có truststore (bước 6).
        @Bean
        JwkSetUriJwtDecoderBuilderCustomizer unreachableJwks(
                @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
            RestTemplate restTemplate = new RestTemplate();
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
            server.expect(ExpectedCount.manyTimes(), requestTo(jwkSetUri))
                    .andRespond(withException(new SSLHandshakeException("PKIX path building failed (test)")));
            return builder -> builder.restOperations(restTemplate);
        }
    }
}
