package io.github.hoangduoc0603.commercehub.hello;

import java.time.Instant;
import java.util.Date;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
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
    // sống 300 s.
    String valid() {
        return sign(validClaims().build());
    }

    // Điểm xuất phát của mọi token xấu ở bước 4: token hợp lệ, test đổi đúng MỘT điểm. Token hợp lệ đã được chứng minh
    // là qua (BR04_validTokenCanCreateGreeting), nên bị từ chối thì điểm vừa đổi là lý do. Mỗi lần gọi lấy giờ mới.
    JWTClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                // audience(String): Nimbus ghi aud dạng chuỗi khi chỉ có một giá trị, như Keycloak.
                .audience(AUDIENCE)
                .subject("service-account-smoke-test")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)));
    }

    String issuer() {
        return issuer;
    }

    // kid thật: kẻ tấn công đọc được từ JWKS công khai.
    String keyId() {
        return key.getKeyID();
    }

    // Public key dạng X.509 DER: thứ kẻ tấn công lấy từ JWKS để làm secret HMAC (alg confusion, RFC 8725 §2.1).
    byte[] publicKeyBytes() {
        try {
            return key.toRSAPublicKey().getEncoded();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    // Ký bằng khóa của "Keycloak giả": token mà decoder tin được.
    String sign(JWTClaimsSet claims) {
        try {
            return sign(JWSAlgorithm.RS256, key.getKeyID(), new RSASSASigner(key), claims);
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    // Ký với thuật toán, kid, khóa tùy ý: dùng để giả làm kẻ tấn công (khóa lạ, HS256).
    static String sign(JWSAlgorithm alg, String keyId, JWSSigner signer, JWTClaimsSet claims) {
        JWSHeader header = new JWSHeader.Builder(alg)
                .keyID(keyId)
                // Security 7 mặc định có JwtTypeValidator.jwt(): typ phải là "JWT" (Keycloak ghi đúng như vậy).
                .type(JOSEObjectType.JWT)
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }
}
