package com.wikigerminare.auth;

import com.jayway.jsonpath.JsonPath;
import com.wikigerminare.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    private UUID userId;
    private String accessToken;

    @BeforeEach
    void loginAsExistingUser() throws Exception {
        userId = UUID.randomUUID();
        String email = "principal-" + userId + "@example.com";
        jdbc.update("""
                INSERT INTO users (id, name, email, password_hash, role)
                VALUES (?, 'Principal test', ?, ?, CAST('admin' AS user_role))
                """, userId, email, passwordEncoder.encode("correct password"));
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        accessToken = JsonPath.read(body, "$.accessToken");
    }

    @Test
    void bearerTokenUsesUuidAsPrincipalAndProtectsExistingRoutes() throws Exception {
        var response = mockMvc.perform(post("/api/folders")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"auth-" + userId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(response, "$.createdBy")).isEqualTo(userId.toString());

        String memberToken = signedToken(userId, "member", Instant.now(), Instant.now().plusSeconds(600));
        mockMvc.perform(post("/api/folders")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"member-" + userId + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/pages")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"slug\":\"member-" + userId + "\",\"content\":\"c\",\"folderId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/folders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredOrTamperedTokenAndPreservesPublicEndpoints() throws Exception {
        String expired = signedToken(userId, "member", Instant.now().minusSeconds(1200), Instant.now().minusSeconds(600));
        mockMvc.perform(get("/api/folders").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());

        String[] parts = accessToken.split("\\.");
        char replacement = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + replacement + parts[2].substring(1);
        mockMvc.perform(get("/api/folders").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/images/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    private String signedToken(UUID subject, String role, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(com.wikigerminare.auth.security.AuthJwtProperties.ISSUER)
                .subject(subject.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("role", role)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
