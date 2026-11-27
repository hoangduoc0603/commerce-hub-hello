package io.github.hoangduoc0603.commercehub.hello;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// G0-29: mọi route bắt JWT của realm commerce-hub (keycloak.md mục "Resource server", BR-04 phần token).
// Token ký thật bằng TestJwts, decoder thật của Boot tải JWKS giả (TestJwtConfiguration).
// Token xấu (bước 4): mỗi case đổi đúng một điểm của token hợp lệ.
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

    // Mỗi case: token hợp lệ (validClaims) đổi đúng MỘT điểm. Comment ghi cửa nào của decoder chặn nó (bước 4 ticket).
    static Stream<Named<Function<TestJwts, String>>> badTokens() {
        return Stream.of(
                // Token Keycloak phát cho client khác cùng realm: cùng khóa, cùng iss. Chỉ audiences chặn được.
                Named.of("sai aud (account)", jwts -> jwts.sign(jwts.validClaims().audience("account").build())),
                // Quá lệch đồng hồ 60 s của JwtTimestampValidator. exp lùi 10 s thì vẫn được nhận.
                Named.of("hết hạn quá lệch 60 s", jwts -> jwts.sign(jwts.validClaims()
                        .issueTime(Date.from(Instant.now().minusSeconds(420)))
                        .expirationTime(Date.from(Instant.now().minusSeconds(120)))
                        .build())),
                // Cùng khóa với realm thật, chỉ khác iss: chứng minh JwtIssuerValidator chạy. Keycloak thật cho mỗi realm
                // một khóa riêng; dùng chung khóa ở đây để cô lập đúng một biến.
                Named.of("realm khác", jwts -> jwts.sign(jwts.validClaims()
                        .issuer(jwts.issuer().replace("/realms/commerce-hub", "/realms/other"))
                        .build())),
                // Kẻ tấn công tự ký, chép kid thật: decoder tìm được khóa, chữ ký sai.
                Named.of("khóa lạ, cùng kid", jwts -> signWithStrangerKey(jwts, jwts.keyId())),
                // kid không có trong JWKS: decoder tải lại JWKS, vẫn không thấy.
                Named.of("khóa lạ, kid lạ", jwts -> signWithStrangerKey(jwts, "stranger")),
                // Alg confusion: HMAC với secret là public key RSA. Bị chặn vì RS256 là thuật toán duy nhất được phép
                // (mặc định của Boot; dòng jws-algorithms trong application.yaml chỉ ghi rõ ra, xóa dòng đó test vẫn xanh).
                // Log TRACE cùng thông báo với "kid lạ" ("Another algorithm expected, or no matching key(s) found").
                Named.of("HS256 ký bằng public key", jwts -> {
                    try {
                        return TestJwts.sign(JWSAlgorithm.HS256, jwts.keyId(),
                                new MACSigner(jwts.publicKeyBytes()), jwts.validClaims().build());
                    } catch (JOSEException e) {
                        throw new IllegalStateException(e);
                    }
                }));
    }

    // Khóa RSA mới sinh, không có trong JWKS: thứ kẻ tấn công có trong tay.
    private static String signWithStrangerKey(TestJwts jwts, String keyId) {
        try {
            RSAKey stranger = new RSAKeyGenerator(2048).generate();
            return TestJwts.sign(JWSAlgorithm.RS256, keyId, new RSASSASigner(stranger), jwts.validClaims().build());
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    // Function thay vì token: @MethodSource phải static, còn bean TestJwts chỉ có sau khi Spring tiêm vào instance.
    @ParameterizedTest
    @MethodSource("badTokens")
    void BR04_badTokenIsUnauthorized(Function<TestJwts, String> token) {
        // ID ngẫu nhiên: token lọt qua Security thì ra 404, nên khi hàng rào hỏng test đỏ với "expected 401 but was 404".
        MvcTestResult result = mvc.get().uri("/v1/greetings/{id}", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.apply(jwts))
                .exchange();
        assertUnauthorizedProblem(result);
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
