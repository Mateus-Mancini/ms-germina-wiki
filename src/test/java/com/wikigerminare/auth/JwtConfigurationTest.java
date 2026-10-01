package com.wikigerminare.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.JWSObject;
import com.wikigerminare.auth.security.AuthJwtProperties;
import com.wikigerminare.auth.security.JwtConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigurationTest {

    private static final String SECRET =
            "VGhpc0lzQVRlc3RPbmx5U2VjcmV0S2V5Rm9yS2V5RGVyaXZhdGlvbg==";

    private JwtDecoder decoder;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        key = new AuthJwtProperties(SECRET, 900).secretKey();
        decoder = new JwtConfiguration().jwtDecoder(key);
    }

    @Test
    void acceptsValidJwtAndRejectsExpiredJwt() {
        Instant now = Instant.now();
        String valid = token(UUID.randomUUID().toString(), "admin", now.minusSeconds(1), now.plusSeconds(300));
        String expired = token(UUID.randomUUID().toString(), "member", now.minusSeconds(1200), now.minusSeconds(600));

        assertThat(decoder.decode(valid).getClaimAsString("role")).isEqualTo("admin");
        assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTamperedSignatureAndUnsupportedAlgorithm() throws Exception {
        String token = token(UUID.randomUUID().toString(), "member",
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(300));
        String[] parts = token.split("\\.");
        char replacement = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        assertThatThrownBy(() -> decoder.decode(parts[0] + "." + parts[1] + "." + replacement + parts[2].substring(1)))
                .isInstanceOf(JwtException.class);

        String[] validParts = token(UUID.randomUUID().toString(), "member",
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(300)).split("\\.");
        String unsupportedHeader = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS384\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> decoder.decode(unsupportedHeader + "." + validParts[1] + "." + validParts[2]))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsMissingOrInvalidIdentityAndRoleClaims() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> decoder.decode(token("not-a-uuid", "admin", now, now.plusSeconds(300))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(UUID.randomUUID().toString(), "owner", now, now.plusSeconds(300))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(tokenWithoutExpiry(UUID.randomUUID().toString(), "member", now)))
                .isInstanceOf(JwtException.class);
    }

    private String token(String subject, String role, Instant issuedAt, Instant expiresAt) {
        String payload = "{\"iss\":\"germinawiki\",\"sub\":\"" + subject
                + "\",\"role\":\"" + role + "\",\"iat\":" + issuedAt.getEpochSecond()
                + ",\"exp\":" + expiresAt.getEpochSecond() + "}";
        return sign(payload, JWSAlgorithm.HS256);
    }

    private String tokenWithoutExpiry(String subject, String role, Instant now) {
        String payload = "{\"iss\":\"germinawiki\",\"sub\":\"" + subject
                + "\",\"role\":\"" + role + "\",\"iat\":" + now.getEpochSecond() + "}";
        return sign(payload, JWSAlgorithm.HS256);
    }

    private String sign(String payload, JWSAlgorithm algorithm) {
        JWSObject object = new JWSObject(new JWSHeader(algorithm), new Payload(payload));
        try {
            object.sign(new MACSigner(key.getEncoded()));
        } catch (com.nimbusds.jose.JOSEException exception) {
            throw new IllegalStateException(exception);
        }
        return object.serialize();
    }
}
