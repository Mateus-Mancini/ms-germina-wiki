package com.wikigerminare.config;

import com.wikigerminare.auth.security.AuthJwtProperties;
import com.wikigerminare.auth.security.JwtConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.ProtectedProbeController.class)
@Import({
        SecurityConfig.class,
        JwtConfiguration.class,
        SecurityConfigTest.ProtectedProbeController.class,
        SecurityConfigTest.WebSecurityTestConfiguration.class
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void protectedRouteRequiresValidBearerToken() throws Exception {
        mockMvc.perform(get("/security-test/protected"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/security-test/protected")
                        .header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/security-test/protected")
                        .header("Authorization", "Bearer " + signedToken(
                                Instant.now().minusSeconds(1200), Instant.now().minusSeconds(600))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/security-test/protected")
                        .header("Authorization", "Bearer " + signedToken(
                                Instant.now().minusSeconds(1), Instant.now().plusSeconds(300))))
                .andExpect(status().isOk());
    }

    @Test
    void existingPublicRoutesRemainAccessible() throws Exception {
        mockMvc.perform(get("/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/images/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private String signedToken(Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(AuthJwtProperties.ISSUER)
                .subject(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("role", "member")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @RestController
    static class ProtectedProbeController {
        @GetMapping(value = "/security-test/protected", produces = MediaType.TEXT_PLAIN_VALUE)
        String protectedRoute() {
            return "authenticated";
        }

        @GetMapping(value = "/health", produces = MediaType.TEXT_PLAIN_VALUE)
        String health() {
            return "ready";
        }
    }

    @TestConfiguration
    @EnableWebSecurity
    static class WebSecurityTestConfiguration {
    }
}
