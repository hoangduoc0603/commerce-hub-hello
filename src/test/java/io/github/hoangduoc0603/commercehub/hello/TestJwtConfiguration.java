package io.github.hoangduoc0603.commercehub.hello;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.JwkSetUriJwtDecoderBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

// Test cần token thật thì @Import class này (G0-29). @TestConfiguration: TestTypeExcludeFilter loại khỏi component scan,
// nên không lọt vào context của test khác nếu không @Import.
@TestConfiguration(proxyBeanMethods = false)
class TestJwtConfiguration {

    // issuer lấy từ đúng property mà JwtIssuerValidator của Boot dùng (src/test/resources/config/application.yaml):
    // token hợp lệ khớp cấu hình, không phải khớp một hằng số thứ hai trong test.
    @Bean
    TestJwts testJwts(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        return new TestJwts(issuer);
    }

    // Thay đường HTTP của decoder: GET jwk-set-uri trả JWKS của TestJwts. Boot gọi mọi customizer trước build()
    // (JwtDecoderConfiguration.buildJwkSetUriJwtDecoder), nên phần còn lại là decoder thật.
    // Thiếu bean này thì decoder gọi https://auth.test thật và request lỗi với AuthenticationServiceException
    // "I/O error on GET request for ...certs" (đã thấy 2026-11-27): lỗi tải khóa không thành 401 (xem bước 6 ticket).
    @Bean
    JwkSetUriJwtDecoderBuilderCustomizer fakeJwksEndpoint(TestJwts jwts,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        // manyTimes: decoder cache JWKS 5 phút và tải lại khi gặp kid lạ (bước 4, token ký bằng khóa lạ).
        server.expect(ExpectedCount.manyTimes(), requestTo(jwkSetUri))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jwts.jwkSetJson(), MediaType.APPLICATION_JSON));
        return builder -> builder.restOperations(restTemplate);
    }
}
