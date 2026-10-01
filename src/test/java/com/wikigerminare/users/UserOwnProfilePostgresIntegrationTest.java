package com.wikigerminare.users;

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
class UserOwnProfilePostgresIntegrationTest {

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
    void createAccountAndAuthenticate() throws Exception {
        userId = UUID.randomUUID();
        String email = "users-me-" + userId + "@example.com";
        jdbc.update("""
                INSERT INTO users (id, name, email, password_hash, role, avatar_url, bio)
                VALUES (?, 'Own profile', ?, ?, CAST('member' AS user_role), 'https://example.com/a.png', 'Private view')
                """, userId, email, passwordEncoder.encode("correct password"));

        String login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        accessToken = JsonPath.read(login, "$.accessToken");
    }

    @Test
    void returnsAuthenticatedUsersOwnProfileWithoutCredentials() throws Exception {
        String response = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(response, "$.id")).isEqualTo(userId.toString());
        assertThat(JsonPath.<String>read(response, "$.email")).isEqualTo("users-me-" + userId + "@example.com");
        assertThat(response).contains("\"name\":\"Own profile\"")
                .doesNotContain("password", "password_hash", "role", "createdAt", "updatedAt");
    }

    @Test
    void rejectsUnauthenticatedOwnProfileRequest() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsNotFoundWhenAuthenticatedIdentityHasNoUserRow() throws Exception {
        UUID absentUserId = UUID.randomUUID();
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + signedToken(absentUserId)))
                .andExpect(status().isNotFound());
    }

    private String signedToken(UUID subject) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(com.wikigerminare.auth.security.AuthJwtProperties.ISSUER)
                .subject(subject.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("role", "member")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
