package com.wikigerminare.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.wikigerminare.auth.security.AuthJwtProperties;
import com.wikigerminare.auth.security.JwtTokenService;
import com.wikigerminare.users.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private static final String SECRET =
            "VGhpc0lzQVRlc3RPbmx5U2VjcmV0S2V5Rm9yS2V5RGVyaXZhdGlvbg==";

    @Test
    void issuesSignedTokenWithUuidRoleAndBoundedClaimsOnly() {
        AuthJwtProperties properties = new AuthJwtProperties(SECRET, 900);
        SecretKey key = properties.secretKey();
        JwtTokenService tokenService = new JwtTokenService(
                new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key.getEncoded())), properties);
        UUID userId = UUID.randomUUID();

        JwtTokenService.IssuedToken issued = tokenService.issue(userId, UserRole.ADMIN);
        Jwt decoded = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()
                .decode(issued.accessToken());

        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getClaimAsString("role")).isEqualTo("admin");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo(AuthJwtProperties.ISSUER);
        assertThat(decoded.getIssuedAt()).isNotNull();
        assertThat(decoded.getExpiresAt()).isEqualTo(issued.expiresAt());
        assertThat(decoded.getExpiresAt()).isEqualTo(decoded.getIssuedAt().plusSeconds(900));
        assertThat(decoded.getClaims()).doesNotContainKeys("email", "password", "password_hash", "name", "bio");
    }
}
