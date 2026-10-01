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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserPublicProfilePostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID targetId;
    private String memberToken;
    private String adminToken;

    @BeforeEach
    void createAccountsAndAuthenticate() throws Exception {
        UUID memberId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        targetId = UUID.randomUUID();
        String memberEmail = "users-public-member-" + memberId + "@example.com";
        String adminEmail = "users-public-admin-" + adminId + "@example.com";
        String targetEmail = "users-public-target-" + targetId + "@example.com";
        jdbc.update("""
                INSERT INTO users (id, name, email, password_hash, role)
                VALUES (?, 'Member caller', ?, ?, CAST('member' AS user_role))
                """, memberId, memberEmail, passwordEncoder.encode("member password"));
        jdbc.update("""
                INSERT INTO users (id, name, email, password_hash, role)
                VALUES (?, 'Admin caller', ?, ?, CAST('admin' AS user_role))
                """, adminId, adminEmail, passwordEncoder.encode("admin password"));
        jdbc.update("""
                INSERT INTO users (id, name, email, password_hash, role, avatar_url, bio)
                VALUES (?, 'Public target', ?, ?, CAST('admin' AS user_role),
                        'https://example.com/public.png', 'Public biography')
                """, targetId, targetEmail, passwordEncoder.encode("target password"));

        memberToken = login(memberEmail, "member password");
        adminToken = login(adminEmail, "admin password");
    }

    @Test
    void memberAndAdminReceiveOnlyThePublicProfileAllowlist() throws Exception {
        String memberResponse = getProfile(memberToken);
        String adminResponse = getProfile(adminToken);

        for (String response : new String[] {memberResponse, adminResponse}) {
            assertThat(JsonPath.<String>read(response, "$.id")).isEqualTo(targetId.toString());
            assertThat(JsonPath.<String>read(response, "$.name")).isEqualTo("Public target");
            assertThat(JsonPath.<String>read(response, "$.avatarUrl"))
                    .isEqualTo("https://example.com/public.png");
            assertThat(JsonPath.<String>read(response, "$.bio")).isEqualTo("Public biography");
            assertThat(response).doesNotContain("email", "password", "password_hash", "role",
                    "createdAt", "updatedAt");
        }
    }

    @Test
    void returnsNotFoundForUnknownPublicProfile() throws Exception {
        mockMvc.perform(get("/api/users/{userId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNotFound());
    }

    private String getProfile(String token) throws Exception {
        return mockMvc.perform(get("/api/users/{userId}", targetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
