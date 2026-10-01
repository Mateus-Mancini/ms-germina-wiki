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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String email;
    private String password;
    private UUID userId;

    @BeforeEach
    void createAccount() {
        userId = UUID.randomUUID();
        email = "auth-" + userId + "@example.com";
        password = "correct horse battery staple";
        jdbc.update("""
                INSERT INTO users (id, name, email, password_hash, role)
                VALUES (?, 'Auth integration', ?, ?, CAST('member' AS user_role))
                """, userId, email, passwordEncoder.encode(password));
    }

    @Test
    void validCredentialsReturnJwtAndIncorrectOrUnknownCredentialsAreIndistinguishable() throws Exception {
        MvcResult success = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        String response = success.getResponse().getContentAsString();
        String token = JsonPath.read(response, "$.accessToken");
        assertThat(token).isNotBlank();
        assertThat(JsonPath.<String>read(response, "$.tokenType")).isEqualTo("Bearer");
        assertThat(response).doesNotContain(password, "passwordHash");

        String invalidPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "wrong password")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("absent-" + userId + "@example.com", password)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(invalidPassword).isEqualTo(unknownEmail)
                .isEqualTo("{\"error\":\"Invalid email or password\"}");
    }

    private static String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
