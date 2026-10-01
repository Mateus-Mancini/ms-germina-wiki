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
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserProfileUpdatePostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID userId;
    private UUID otherUserId;
    private String email;
    private String passwordHash;
    private String accessToken;

    @BeforeEach
    void createAccountsAndAuthenticate() throws Exception {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        email = "users-patch-" + userId + "@example.com";
        passwordHash = passwordEncoder.encode("correct password");
        jdbc.update("""
                INSERT INTO users (id, name, email, password_hash, role, avatar_url, bio)
                VALUES (?, 'Before', ?, ?, CAST('member' AS user_role), 'https://example.com/before.png', 'Before bio')
                """, userId, email, passwordHash);
        jdbc.update("""
                INSERT INTO users (id, name, email, password_hash, role, avatar_url, bio)
                VALUES (?, 'Other user', ?, ?, CAST('admin' AS user_role), 'https://example.com/other.png', 'Other bio')
                """, otherUserId, "other-" + otherUserId + "@example.com",
                passwordEncoder.encode("other password"));

        String login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        accessToken = JsonPath.read(login, "$.accessToken");
    }

    @Test
    void updatesOnlyAuthenticatedAccountAndPreservesOmittedAndInternalFields() throws Exception {
        OffsetDateTime before = jdbc.queryForObject(
                "SELECT updated_at FROM users WHERE id = ?", OffsetDateTime.class, userId);

        String response = mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated name\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(response, "$.name")).isEqualTo("Updated name");
        assertThat(response).doesNotContain("password", "password_hash", "role");
        assertThat(jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String.class, userId))
                .isEqualTo("Updated name");
        assertThat(jdbc.queryForObject("SELECT avatar_url FROM users WHERE id = ?", String.class, userId))
                .isEqualTo("https://example.com/before.png");
        assertThat(jdbc.queryForObject("SELECT bio FROM users WHERE id = ?", String.class, userId))
                .isEqualTo("Before bio");
        assertThat(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId))
                .isEqualTo(email);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, userId))
                .isEqualTo(passwordHash);
        assertThat(jdbc.queryForObject("SELECT role::text FROM users WHERE id = ?", String.class, userId))
                .isEqualTo("member");
        assertThat(jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String.class, otherUserId))
                .isEqualTo("Other user");
        assertThat(jdbc.queryForObject("SELECT updated_at FROM users WHERE id = ?", OffsetDateTime.class, userId))
                .isAfterOrEqualTo(before);
    }

    @Test
    void explicitNullClearsNullableProfileFieldsAndClientCannotSelectAnotherAccount() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUrl\":null,\"bio\":null}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT avatar_url FROM users WHERE id = ?", String.class, userId))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT bio FROM users WHERE id = ?", String.class, userId))
                .isNull();

        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Attacker\",\"id\":\"" + otherUserId + "\"}"))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String.class, userId))
                .isEqualTo("Before");
        assertThat(jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String.class, otherUserId))
                .isEqualTo("Other user");
    }
}
