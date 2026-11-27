package io.github.hoangduoc0603.commercehub.hello;

import java.time.Instant;
import java.util.Date;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

// "Keycloak giả" của test (G0-29): giữ khóa RSA, xuất JWKS công khai, ký token. Là một bean (TestJwtConfiguration)
// để test và JwtDecoder (qua JWKS giả) dùng CHUNG một khóa trong cùng context.
// Khóa sinh trong bộ nhớ mỗi lần context khởi động: repo không chứa file khóa riêng nào, kể cả khóa chỉ dùng cho test.
// Nimbus có sẵn: spring-security-oauth2-jose (kéo theo bởi starter resource server) dùng nó để kiểm token.
class TestJwts {

    static final String AUDIENCE = "commerce-hub-api";

    private final RSAKey key;
    private final String issuer;

    TestJwts(String issuer) {
        this.issuer = issuer;
        try {
            // 2048 bit như khóa RS256 mặc định của Keycloak. kid phải khớp giữa header token và JWKS: decoder chọn
            // khóa theo kid.
            this.key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    // JWKS chỉ có phần công khai, giống .../protocol/openid-connect/certs của Keycloak.
    // JWKSet.toString() mặc định bỏ phần riêng tư; toPublicJWK() cho chắc ngay ở nguồn.
    String jwkSetJson() {
        return new JWKSet(key.toPublicJWK()).toString();
    }

    // Giống token client credentials của smoke-test đo ở G0-11: RS256, typ JWT, aud là CHUỖI (không phải mảng),
    // sống 300 s. Bước 4 thêm các biến thể xấu từ đây.
    String valid() {
        Instant now = Instant.now();
        return sign(new JWTClaimsSet.Builder()
                .issuer(issuer)
                // audience(String): Nimbus ghi aud dạng chuỗi khi chỉ có một giá trị, như Keycloak.
                .audience(AUDIENCE)
                .subject("service-account-smoke-test")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build());
    }

    String sign(JWTClaimsSet claims) {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(key.getKeyID())
                // Security 7 mặc định có JwtTypeValidator.jwt(): typ phải là "JWT" (Keycloak ghi đúng như vậy).
                .type(JOSEObjectType.JWT)
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new RSASSASigner(key));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }
}
